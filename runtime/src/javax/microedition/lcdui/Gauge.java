package javax.microedition.lcdui;

public class Gauge extends Item {
    public static final int INDEFINITE = -1;
    public static final int CONTINUOUS_IDLE = 0;
    public static final int INCREMENTAL_IDLE = 1;
    public static final int CONTINUOUS_RUNNING = 2;
    public static final int INCREMENTAL_UPDATING = 3;

    private final boolean interactive;
    private int max, value;

    public Gauge(String label, boolean interactive, int maxValue, int initialValue) {
        super(label);
        this.interactive = interactive;
        this.max = maxValue;
        setValue(initialValue);
    }

    public void setValue(int v) {
        if (max != INDEFINITE) v = Math.max(0, Math.min(v, max));
        value = v;
        repaintOwner();
    }

    public int getValue() { return value; }

    public void setMaxValue(int m) {
        max = m;
        if (max != INDEFINITE && value > max) value = max;
        repaintOwner();
    }

    public int getMaxValue() { return max; }

    public boolean isInteractive() { return interactive; }

    int contentHeight(int w) { return 12; }

    void paintContent(Graphics g, int x, int y, int w, boolean focused) {
        g.setColor(focused ? Screen.HL_BG : Screen.BORDER);
        g.drawRect(x, y, w - 1, 10);
        int fill = max > 0 ? (w - 4) * value / max : (w - 4) / 3;
        g.setColor(0x40A040);
        g.fillRect(x + 2, y + 2, fill, 7);
    }

    boolean j4iFocusable() { return interactive || !cmds.isEmpty(); }

    boolean j4iKey(int code) {
        if (interactive && max > 0) {
            int ga = Screen.gameAction(code);
            if (ga == Canvas.LEFT || ga == Canvas.RIGHT) {
                setValue(value + (ga == Canvas.LEFT ? -1 : 1));
                notifyStateChanged();
                return true;
            }
        }
        return super.j4iKey(code);
    }

    void j4iTap(int x, int y, int w) {
        if (interactive && max > 0) {
            setValue(max * Math.max(0, Math.min(x, w)) / Math.max(1, w));
            notifyStateChanged();
        }
    }
}
