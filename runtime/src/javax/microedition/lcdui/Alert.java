package javax.microedition.lcdui;

import java.util.Vector;

public class Alert extends Screen {
    public static final Command DISMISS_COMMAND = new Command("Done", Command.OK, 0);
    public static final int FOREVER = -2;

    private String text;
    private Image image;
    private AlertType type;
    private int timeout = getDefaultTimeout();
    private Gauge indicator;
    Displayable next;
    private long shownAt;

    public Alert(String title) { this(title, null, null, null); }

    public Alert(String title, String alertText, Image alertImage, AlertType alertType) {
        this.title = title;
        this.text = alertText;
        this.image = alertImage;
        this.type = alertType;
    }

    public int getDefaultTimeout() { return 2000; }

    public int getTimeout() { return timeout; }

    public void setTimeout(int time) {
        if (time <= 0 && time != FOREVER) throw new IllegalArgumentException();
        timeout = time;
    }

    public AlertType getType() { return type; }

    public void setType(AlertType t) { type = t; }

    public String getString() { return text; }

    public void setString(String s) {
        text = s;
        repaintSelf();
    }

    public Image getImage() { return image; }

    public void setImage(Image img) {
        image = img;
        repaintSelf();
    }

    public void setIndicator(Gauge g) {
        indicator = g;
        repaintSelf();
    }

    public Gauge getIndicator() { return indicator; }

    public void addCommand(Command cmd) {
        if (cmd == DISMISS_COMMAND) return;
        super.addCommand(cmd);
    }

    public void removeCommand(Command cmd) {
        if (cmd == DISMISS_COMMAND) return;
        super.removeCommand(cmd);
    }

    Command[] sortedCommands() {
        Command[] c = super.sortedCommands();
        return c.length == 0 ? new Command[] {DISMISS_COMMAND} : c;
    }

    void j4iShown(Displayable previous) {
        shownAt = System.currentTimeMillis();
        if (next == null && !(previous instanceof Alert)) next = previous;
    }

    long timeLeft() {
        if (timeout == FOREVER || !cmds.isEmpty()) return -1;
        return Math.max(0, shownAt + timeout - System.currentTimeMillis());
    }

    void j4iTick() {
        if (isShown() && timeLeft() == 0) dismiss();
    }

    void dismiss() {
        Displayable n = next;
        next = null;
        if (n != null) Display.instance.setCurrent(n);
    }

    int contentHeight(int w) {
        int h = 8;
        if (image != null) h += image.h + 6;
        if (text != null) h += Screen.wrap(text, textFont(), w - 12).size() * textFont().getHeight();
        if (indicator != null) h += 18;
        return h;
    }

    void paintContent(Graphics g, int w, int h) {
        g.translate(0, -scroll);
        int y = 6;
        if (image != null) {
            g.drawImage(image, w / 2, y, Graphics.HCENTER | Graphics.TOP);
            y += image.h + 6;
        }
        if (text != null) {
            Font f = textFont();
            g.setFont(f);
            g.setColor(FG);
            Vector lines = Screen.wrap(text, f, w - 12);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString((String) lines.elementAt(i), w / 2, y, Graphics.HCENTER | Graphics.TOP);
                y += f.getHeight();
            }
        }
        if (indicator != null) indicator.paintContent(g, 10, y + 4, w - 20, false);
    }

    void j4iKey(int type, int code) {
        if (type != Display.EV_KEY_PRESSED) return;
        int ga = gameAction(code);
        if (ga == Canvas.UP || ga == Canvas.DOWN) scrollBy((ga == Canvas.UP ? -1 : 1) * textFont().getHeight());
        else if (ga == Canvas.FIRE && cmds.isEmpty()) {
            if (listener != null) Display.instance.fireCommand(this, DISMISS_COMMAND);
            else dismiss();
        }
    }
}
