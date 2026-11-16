package javax.microedition.lcdui;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class DateField extends Item {
    public static final int DATE = 1;
    public static final int TIME = 2;
    public static final int DATE_TIME = 3;

    private Date date;
    private int mode;
    private final TimeZone tz;

    public DateField(String label, int mode) { this(label, mode, null); }

    public DateField(String label, int mode, TimeZone timeZone) {
        super(label);
        if (mode < DATE || mode > DATE_TIME) throw new IllegalArgumentException();
        this.mode = mode;
        this.tz = timeZone != null ? timeZone : TimeZone.getDefault();
    }

    public Date getDate() { return date; }

    public void setDate(Date d) {
        date = d;
        repaintOwner();
    }

    public int getInputMode() { return mode; }

    public void setInputMode(int m) {
        if (m < DATE || m > DATE_TIME) throw new IllegalArgumentException();
        mode = m;
        repaintOwner();
    }

    private static String two(int v) { return v < 10 ? "0" + v : String.valueOf(v); }

    String text() {
        if (date == null) return "<date>";
        Calendar c = Calendar.getInstance(tz);
        c.setTime(date);
        String d = two(c.get(Calendar.DAY_OF_MONTH)) + "/" + two(c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.YEAR);
        String t = two(c.get(Calendar.HOUR_OF_DAY)) + ":" + two(c.get(Calendar.MINUTE));
        return mode == DATE ? d : mode == TIME ? t : d + " " + t;
    }

    int contentHeight(int w) { return Font.getDefaultFont().getHeight() + 6; }

    void paintContent(Graphics g, int x, int y, int w, boolean focused) {
        Font f = Font.getDefaultFont();
        g.setColor(focused ? Screen.HL_BG : Screen.BORDER);
        g.drawRect(x, y, w - 1, f.getHeight() + 4);
        g.setColor(Screen.FG);
        g.setFont(f);
        g.drawString(text(), x + 4, y + 3, Graphics.LEFT | Graphics.TOP);
    }

    boolean j4iFocusable() { return true; }
}
