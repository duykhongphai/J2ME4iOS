package javax.microedition.lcdui;

import java.util.Vector;

public abstract class Displayable {
    String title;
    Ticker ticker;
    final Vector cmds = new Vector();
    CommandListener listener;

    Displayable() {}

    public String getTitle() { return title; }

    public void setTitle(String s) {
        title = s;
        repaintSelf();
    }

    public Ticker getTicker() { return ticker; }

    public void setTicker(Ticker t) {
        ticker = t;
        repaintSelf();
    }

    public boolean isShown() {
        Display d = Display.instance;
        return d != null && d.current == this;
    }

    public void addCommand(Command cmd) {
        if (cmd == null) throw new NullPointerException();
        if (!cmds.contains(cmd)) {
            cmds.addElement(cmd);
            repaintSelf();
        }
    }

    public void removeCommand(Command cmd) {
        if (cmds.removeElement(cmd)) repaintSelf();
    }

    public void setCommandListener(CommandListener l) { listener = l; }

    public int getWidth() { return Display.W; }

    public int getHeight() { return Display.H; }

    protected void sizeChanged(int w, int h) {}

    /* ---------- J2ME4iOS internals ---------- */
    void repaintSelf() {
        Display d = Display.instance;
        if (d != null) d.repaintFrom(this);
    }

    Command[] sortedCommands() {
        Command[] c;
        synchronized (cmds) {
            c = new Command[cmds.size()];
            cmds.copyInto(c);
        }
        for (int i = 1; i < c.length; i++) {
            Command v = c[i];
            int j = i - 1;
            while (j >= 0 && c[j].getPriority() > v.getPriority()) {
                c[j + 1] = c[j];
                j--;
            }
            c[j + 1] = v;
        }
        return c;
    }

    boolean hasSoftBar() { return true; }

    void j4iShow() {}

    void j4iHide() {}

    abstract void j4iPaint(Graphics g);

    void j4iKey(int type, int code) {}

    void j4iPointer(int type, int x, int y) {}
}
