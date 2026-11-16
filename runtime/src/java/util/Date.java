package java.util;

public class Date {
    private long time;

    public Date() { this(System.currentTimeMillis()); }

    public Date(long date) { time = date; }

    public long getTime() { return time; }

    public void setTime(long t) { time = t; }

    public boolean equals(Object obj) { return obj instanceof Date && ((Date) obj).time == time; }

    public int hashCode() { return (int) time ^ (int) (time >> 32); }

    public String toString() {
        Calendar c = Calendar.getInstance();
        c.setTime(this);
        String[] days = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        return days[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + months[c.get(Calendar.MONTH)] + " " + two(c.get(Calendar.DAY_OF_MONTH)) + " "
            + two(c.get(Calendar.HOUR_OF_DAY)) + ":" + two(c.get(Calendar.MINUTE)) + ":" + two(c.get(Calendar.SECOND)) + " "
            + c.getTimeZone().getID() + " " + c.get(Calendar.YEAR);
    }

    private static String two(int v) { return v < 10 ? "0" + v : String.valueOf(v); }
}
