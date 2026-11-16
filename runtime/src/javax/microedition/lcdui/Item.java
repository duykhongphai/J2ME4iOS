package javax.microedition.lcdui;

import java.util.Vector;

public abstract class Item {
    public static final int LAYOUT_DEFAULT = 0;
    public static final int LAYOUT_LEFT = 1;
    public static final int LAYOUT_RIGHT = 2;
    public static final int LAYOUT_CENTER = 3;
    public static final int LAYOUT_TOP = 0x10;
    public static final int LAYOUT_BOTTOM = 0x20;
    public static final int LAYOUT_VCENTER = 0x30;
    public static final int LAYOUT_NEWLINE_BEFORE = 0x100;
    public static final int LAYOUT_NEWLINE_AFTER = 0x200;
    public static final int LAYOUT_SHRINK = 0x400;
    public static final int LAYOUT_EXPAND = 0x800;
    public static final int LAYOUT_VSHRINK = 0x1000;
    public static final int LAYOUT_VEXPAND = 0x2000;
    public static final int LAYOUT_2 = 0x4000;
    public static final int PLAIN = 0;
    public static final int HYPERLINK = 1;
    public static final int BUTTON = 2;

    String label;
    Screen owner;
    int layout;
    final Vector cmds = new Vector();
    ItemCommandListener icl;
    Command defaultCmd;
    int prefW = -1, prefH = -1;

    Item(String label) { this.label = label; }

    public void setLabel(String l) {
        label = l;
        repaintOwner();
    }

    public String getLabel() { return label; }

    public int getLayout() { return layout; }

    public void setLayout(int l) { layout = l; }

    public void addCommand(Command cmd) {
        if (cmd == null) throw new NullPointerException();
        if (!cmds.contains(cmd)) cmds.addElement(cmd);
        repaintOwner();
    }

    public void removeCommand(Command cmd) {
        cmds.removeElement(cmd);
        if (cmd == defaultCmd) defaultCmd = null;
        repaintOwner();
    }

    public void setItemCommandListener(ItemCommandListener l) { icl = l; }

    public int getPreferredWidth() { return prefW >= 0 ? prefW : Display.W - 8; }

    public int getPreferredHeight() { return prefH >= 0 ? prefH : j4iHeight(Display.W - 8); }

    public void setPreferredSize(int width, int height) {
        prefW = width;
        prefH = height;
        repaintOwner();
    }

    public int getMinimumWidth() { return 16; }

    public int getMinimumHeight() { return Font.getDefaultFont().getHeight(); }

    public void setDefaultCommand(Command cmd) {
        defaultCmd = cmd;
        if (cmd != null) addCommand(cmd);
    }

    public void notifyStateChanged() {
        if (owner instanceof Form) ((Form) owner).stateChanged(this);
    }

    /* ---------- internals ---------- */
    void repaintOwner() {
        if (owner != null) owner.repaintSelf();
    }

    int labelHeight() { return label != null && label.length() > 0 ? Screen.labelFont().getHeight() + 2 : 0; }

    int paintLabel(Graphics g, int x, int y, int w) {
        if (label == null || label.length() == 0) return y;
        Font f = Screen.labelFont();
        g.setFont(f);
        g.setColor(Screen.FG);
        g.drawString(Screen.clipText(label, f, w), x, y, Graphics.LEFT | Graphics.TOP);
        return y + f.getHeight() + 2;
    }

    int j4iHeight(int w) { return labelHeight() + contentHeight(w); }

    int contentHeight(int w) { return 0; }

    void j4iPaint(Graphics g, int x, int y, int w, boolean focused) {
        int cy = paintLabel(g, x, y, w);
        paintContent(g, x, cy, w, focused);
    }

    void paintContent(Graphics g, int x, int y, int w, boolean focused) {}

    boolean j4iFocusable() { return !cmds.isEmpty(); }

    /** Returns true if the key was consumed by the item. */
    boolean j4iKey(int code) {
        if (Screen.gameAction(code) == Canvas.FIRE) {
            Command c = defaultCmd != null ? defaultCmd : (cmds.isEmpty() ? null : (Command) cmds.elementAt(0));
            if (c != null && icl != null) {
                try {
                    icl.commandAction(c, this);
                } catch (Throwable t) {
                    j4i.MidletRunner.report("itemCommand", t);
                }
                return true;
            }
        }
        return false;
    }

    void j4iTap(int x, int y, int w) { j4iKey(-5); }

    boolean j4iMoveInside(int dir) { return false; }

    void j4iShow() {}

    void j4iHide() {}
}
