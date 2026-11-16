package javax.microedition.lcdui;

import java.util.Vector;
import javax.microedition.midlet.MIDlet;
import j4i.Sys;

public class Display {
    public static final int LIST_ELEMENT = 1;
    public static final int CHOICE_GROUP_ELEMENT = 2;
    public static final int ALERT = 3;
    public static final int COLOR_BACKGROUND = 0;
    public static final int COLOR_FOREGROUND = 1;
    public static final int COLOR_HIGHLIGHTED_BACKGROUND = 2;
    public static final int COLOR_HIGHLIGHTED_FOREGROUND = 3;
    public static final int COLOR_BORDER = 4;
    public static final int COLOR_HIGHLIGHTED_BORDER = 5;

    /* event types, must match j4i.h */
    static final int EV_KEY_PRESSED = 1, EV_KEY_RELEASED = 2, EV_KEY_REPEATED = 3, EV_POINTER_PRESSED = 4,
        EV_POINTER_RELEASED = 5, EV_POINTER_DRAGGED = 6, EV_PAUSE = 7, EV_RESUME = 8, EV_EXIT = 9, EV_TEXT_INPUT = 10;

    static Display instance;
    static final Object LOCK = new Object();
    static int W, H;
    static int[] screen;
    static Graphics sg;

    Displayable current;
    private final Vector serial = new Vector();
    private final Vector shows = new Vector();
    private boolean repaintPending;

    /* command menu overlay */
    Command[] menu;
    int menuSel;
    private Displayable menuOwner;

    /* key repeat */
    private int heldKey;
    private long repeatAt;
    private boolean pointerInSoftBar;

    private Display() {}

    /** Called once by the launcher before the MIDlet is constructed. */
    public static void j4iInit() {
        W = Sys.screenWidth();
        H = Sys.screenHeight();
        screen = new int[W * H];
        for (int i = 0; i < screen.length; i++) screen[i] = 0xFF000000;
        sg = new Graphics(screen, W, H, null);
        instance = new Display();
    }

    public static Display j4iInstance() { return instance; }

    public static Display getDisplay(MIDlet m) {
        if (m == null) throw new NullPointerException();
        return instance;
    }

    public int getColor(int colorSpecifier) {
        switch (colorSpecifier) {
        case COLOR_BACKGROUND: return 0xFFFFFF;
        case COLOR_FOREGROUND: return 0x000000;
        case COLOR_HIGHLIGHTED_BACKGROUND: return 0x2060C0;
        case COLOR_HIGHLIGHTED_FOREGROUND: return 0xFFFFFF;
        case COLOR_BORDER: return 0x808080;
        case COLOR_HIGHLIGHTED_BORDER: return 0x2060C0;
        }
        throw new IllegalArgumentException();
    }

    public int getBorderStyle(boolean highlighted) { return highlighted ? Graphics.SOLID : Graphics.DOTTED; }

    public boolean isColor() { return true; }

    public int numColors() { return 1 << 24; }

    public int numAlphaLevels() { return 256; }

    public boolean flashBacklight(int duration) { return true; }

    public boolean vibrate(int duration) {
        if (duration < 0) throw new IllegalArgumentException();
        Sys.vibrate(duration);
        return true;
    }

    public int getBestImageWidth(int imageType) { return W / 4; }

    public int getBestImageHeight(int imageType) { return W / 4; }

    public Displayable getCurrent() { return current; }

    public void setCurrent(Displayable next) {
        if (next == null) return;
        Displayable old;
        synchronized (LOCK) {
            old = current;
            if (old == next) {
                requestRepaint();
                return;
            }
            current = next;
            menu = null;
        }
        if (next instanceof Alert) ((Alert) next).j4iShown(old);
        synchronized (shows) {
            shows.addElement(old);
            shows.addElement(next);
        }
        requestRepaint();
    }

    public void setCurrent(Alert alert, Displayable nextDisplayable) {
        if (alert == null || nextDisplayable == null) throw new NullPointerException();
        if (nextDisplayable instanceof Alert) throw new IllegalArgumentException();
        alert.next = nextDisplayable;
        setCurrent(alert);
    }

    public void setCurrentItem(Item item) {
        if (item.owner != null) {
            if (item.owner instanceof Form) ((Form) item.owner).focusItem(item);
            setCurrent(item.owner);
        }
    }

    public void callSerially(Runnable r) {
        synchronized (serial) {
            serial.addElement(r);
        }
        Sys.wakeEvents();
    }

    /* ---------------- painting ---------------- */
    void requestRepaint() {
        repaintPending = true;
        Sys.wakeEvents();
    }

    void repaintFrom(Displayable d) {
        if (d == current) requestRepaint();
    }

    void serviceRepaints() {
        if (repaintPending) paintNow();
    }

    void paintNow() {
        synchronized (LOCK) {
            repaintPending = false;
            Displayable d = current;
            if (d == null) return;
            sg.reset();
            try {
                d.j4iPaint(sg);
            } catch (Throwable t) {
                j4i.MidletRunner.report("paint", t);
            }
            sg.reset();
            if (menu != null) paintMenu(sg);
            Sys.flush(screen, W, H);
        }
    }

    /** GameCanvas.flushGraphics: copy the off-screen buffer directly. */
    void flushCanvasBuffer(Canvas c, Image buf, int x, int y, int w, int h) {
        synchronized (LOCK) {
            if (current != c) return;
            sg.reset();
            sg.setClip(x, y, w, h);
            sg.drawImage(buf, 0, 0, Graphics.TOP | Graphics.LEFT);
            sg.reset();
            c.paintOverlay(sg);
            if (menu != null) paintMenu(sg);
            Sys.flush(screen, W, H);
        }
    }

    /* ---------------- soft keys / commands ---------------- */
    static int softBarHeight() { return Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL).getHeight() + 4; }

    static boolean isNegative(Command c) {
        int t = c.getCommandType();
        return t == Command.BACK || t == Command.CANCEL || t == Command.EXIT || t == Command.STOP;
    }

    /** layout[0] = left command or null, layout[1] = right command, others = menu entries. */
    static Object[] softLayout(Displayable d) {
        Command[] cmds = d.sortedCommands();
        Command right = null;
        for (int i = 0; i < cmds.length; i++) {
            if (isNegative(cmds[i])) {
                right = cmds[i];
                break;
            }
        }
        Vector others = new Vector();
        for (int i = 0; i < cmds.length; i++) if (cmds[i] != right) others.addElement(cmds[i]);
        Command left = null;
        Command[] menuCmds = null;
        if (right == null && others.size() == 2) {
            left = (Command) others.elementAt(0);
            right = (Command) others.elementAt(1);
        } else if (others.size() == 1) {
            left = (Command) others.elementAt(0);
        } else if (others.size() > 1) {
            menuCmds = new Command[others.size()];
            others.copyInto(menuCmds);
        }
        return new Object[] {left, right, menuCmds};
    }

    static void paintSoftBar(Graphics g, Displayable d, boolean overlay) {
        Object[] l = softLayout(d);
        Command left = (Command) l[0], right = (Command) l[1];
        Command[] m = (Command[]) l[2];
        String ls = left != null ? left.getLabel() : (m != null ? "Menu" : null);
        String rs = right != null ? right.getLabel() : null;
        if (ls == null && rs == null && overlay) return;
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        int bh = f.getHeight() + 4;
        g.setFont(f);
        if (!overlay) {
            g.setColor(0x202020);
            g.fillRect(0, H - bh, W, bh);
        }
        if (ls != null) {
            if (overlay) {
                g.setColor(0x202020);
                g.fillRect(0, H - bh, f.stringWidth(ls) + 8, bh);
            }
            g.setColor(0xFFFFFF);
            g.drawString(ls, 4, H - 2, Graphics.LEFT | Graphics.BOTTOM);
        }
        if (rs != null) {
            if (overlay) {
                g.setColor(0x202020);
                g.fillRect(W - f.stringWidth(rs) - 8, H - bh, f.stringWidth(rs) + 8, bh);
            }
            g.setColor(0xFFFFFF);
            g.drawString(rs, W - 4, H - 2, Graphics.RIGHT | Graphics.BOTTOM);
        }
    }

    /** Returns true if the soft key was consumed by a command. */
    boolean softKey(Displayable d, boolean leftKey) {
        Object[] l = softLayout(d);
        Command c = (Command) (leftKey ? l[0] : l[1]);
        if (c != null) {
            fireCommand(d, c);
            return true;
        }
        if (leftKey && l[2] != null) {
            menu = (Command[]) l[2];
            menuSel = 0;
            menuOwner = d;
            requestRepaint();
            return true;
        }
        return false;
    }

    void fireCommand(Displayable d, Command c) {
        try {
            if (d instanceof Form && ((Form) d).fireItemCommand(c)) return;
        } catch (Throwable t) {
            j4i.MidletRunner.report("itemCommand", t);
            return;
        }
        CommandListener cl = d.listener;
        if (cl == null) {
            if (d instanceof Alert) ((Alert) d).dismiss();
            return;
        }
        try {
            cl.commandAction(c, d);
        } catch (Throwable t) {
            j4i.MidletRunner.report("commandAction", t);
        }
    }

    private void paintMenu(Graphics g) {
        Font f = Font.getDefaultFont();
        int rh = f.getHeight() + 6;
        int bh = softBarHeight();
        int mh = menu.length * rh + 4;
        int maxh = H - bh - 8;
        if (mh > maxh) mh = maxh;
        int y0 = H - bh - mh;
        g.setColor(0x303030);
        g.fillRect(0, y0, W, mh);
        g.setColor(0x808080);
        g.drawRect(0, y0, W - 1, mh - 1);
        g.setFont(f);
        int first = 0;
        int visible = (mh - 4) / rh;
        if (menuSel >= visible) first = menuSel - visible + 1;
        for (int i = first; i < menu.length && i - first < visible; i++) {
            int y = y0 + 2 + (i - first) * rh;
            if (i == menuSel) {
                g.setColor(0x2060C0);
                g.fillRect(2, y, W - 4, rh);
            }
            g.setColor(0xFFFFFF);
            g.drawString((i + 1) + ". " + menu[i].getLabel(), 8, y + 3, Graphics.LEFT | Graphics.TOP);
        }
        g.setColor(0x202020);
        g.fillRect(0, H - bh, W, bh);
        g.setColor(0xFFFFFF);
        Font sf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        g.setFont(sf);
        g.drawString("Select", 4, H - 2, Graphics.LEFT | Graphics.BOTTOM);
        g.drawString("Cancel", W - 4, H - 2, Graphics.RIGHT | Graphics.BOTTOM);
    }

    private void menuKey(int code) {
        int ga = code == -1 || code == '2' ? 1 : code == -2 || code == '8' ? 6 : 0;
        if (ga == 1) menuSel = (menuSel + menu.length - 1) % menu.length;
        else if (ga == 6) menuSel = (menuSel + 1) % menu.length;
        else if (code == -5 || code == '5' || code == -6) {
            Command c = menu[menuSel];
            Displayable d = menuOwner;
            menu = null;
            requestRepaint();
            fireCommand(d, c);
            return;
        } else if (code == -7 || code == -8) {
            menu = null;
        } else if (code >= '1' && code <= '9' && code - '1' < menu.length) {
            Command c = menu[code - '1'];
            Displayable d = menuOwner;
            menu = null;
            requestRepaint();
            fireCommand(d, c);
            return;
        }
        requestRepaint();
    }

    /* ---------------- event loop (event thread) ---------------- */
    /** Runs queued callSerially / visibility work and pending repaints. */
    public void j4iProcessPending() {
        while (true) {
            Displayable a = null, b = null;
            synchronized (shows) {
                if (shows.size() >= 2) {
                    a = (Displayable) shows.elementAt(0);
                    b = (Displayable) shows.elementAt(1);
                    shows.removeElementAt(0);
                    shows.removeElementAt(0);
                } else break;
            }
            try {
                if (a != null) a.j4iHide();
                b.j4iShow();
            } catch (Throwable t) {
                j4i.MidletRunner.report("showNotify", t);
            }
        }
        Object[] tasks = null;
        synchronized (serial) {
            if (!serial.isEmpty()) {
                tasks = new Object[serial.size()];
                serial.copyInto(tasks);
                serial.removeAllElements();
            }
        }
        if (repaintPending) paintNow();
        if (tasks != null) {
            for (int i = 0; i < tasks.length; i++) {
                try {
                    ((Runnable) tasks[i]).run();
                } catch (Throwable t) {
                    j4i.MidletRunner.report("callSerially", t);
                }
            }
        }
        if (heldKey != 0 && System.currentTimeMillis() >= repeatAt) {
            repeatAt = System.currentTimeMillis() + 100;
            Displayable d = current;
            if (d != null && menu == null) {
                try {
                    d.j4iKey(EV_KEY_REPEATED, heldKey);
                } catch (Throwable t) {
                    j4i.MidletRunner.report("keyRepeated", t);
                }
            }
        }
        Displayable d = current;
        if (d instanceof Alert) ((Alert) d).j4iTick();
    }

    /** Milliseconds the event loop may sleep, -1 = until the next event. */
    public int j4iTimeout() {
        synchronized (serial) {
            if (!serial.isEmpty()) return 0;
        }
        long t = -1;
        if (heldKey != 0) t = Math.max(1, repeatAt - System.currentTimeMillis());
        Displayable d = current;
        if (d instanceof Alert) {
            long at = ((Alert) d).timeLeft();
            if (at >= 0 && (t < 0 || at < t)) t = Math.max(1, at);
        }
        if (javax.microedition.media.Manager.j4iActive() && (t < 0 || t > 200)) t = 200;
        return (int) t;
    }

    public void j4iEvent(int type, int a, int b) {
        Displayable d = current;
        try {
            switch (type) {
            case EV_KEY_PRESSED:
                heldKey = a;
                repeatAt = System.currentTimeMillis() + 450;
                if (menu != null) {
                    menuKey(a);
                    return;
                }
                if (d == null) return;
                if ((a == -6 || a == -7) && softKey(d, a == -6)) return;
                d.j4iKey(EV_KEY_PRESSED, a);
                break;
            case EV_KEY_RELEASED:
                if (heldKey == a) heldKey = 0;
                if (d != null && menu == null) d.j4iKey(EV_KEY_RELEASED, a);
                break;
            case EV_KEY_REPEATED:
                if (d != null && menu == null) d.j4iKey(EV_KEY_REPEATED, a);
                break;
            case EV_POINTER_PRESSED:
            case EV_POINTER_RELEASED:
            case EV_POINTER_DRAGGED:
                pointer(d, type, a, b);
                break;
            case EV_PAUSE:
                if (d != null) d.j4iHide();
                j4i.MidletRunner.pause();
                break;
            case EV_RESUME:
                j4i.MidletRunner.resume();
                if (current != null) current.j4iShow();
                requestRepaint();
                break;
            case EV_EXIT:
                j4i.MidletRunner.exitRequested();
                break;
            case EV_TEXT_INPUT:
                TextInput.complete(a, b != 0);
                break;
            }
        } catch (Throwable t) {
            j4i.MidletRunner.report("event", t);
        }
    }

    private void pointer(Displayable d, int type, int x, int y) {
        int bh = softBarHeight();
        if (menu != null) {
            if (type != EV_POINTER_RELEASED) return;
            if (y >= H - bh) {
                menuKey(x < W / 2 ? -6 : -7);
                return;
            }
            Font f = Font.getDefaultFont();
            int rh = f.getHeight() + 6;
            int mh = Math.min(menu.length * rh + 4, H - bh - 8);
            int y0 = H - bh - mh;
            int visible = (mh - 4) / rh;
            int first = menuSel >= visible ? menuSel - visible + 1 : 0;
            int idx = first + (y - y0 - 2) / rh;
            if (y < y0 || idx < 0 || idx >= menu.length) {
                menu = null;
                requestRepaint();
                return;
            }
            menuSel = idx;
            menuKey(-5);
            return;
        }
        if (d == null) return;
        boolean bar = d.hasSoftBar() && y >= H - bh && d.sortedCommands().length > 0;
        if (type == EV_POINTER_PRESSED) pointerInSoftBar = bar;
        if (pointerInSoftBar) {
            if (type == EV_POINTER_RELEASED && bar) softKey(d, x < W / 2);
            return;
        }
        d.j4iPointer(type, x, y);
    }
}
