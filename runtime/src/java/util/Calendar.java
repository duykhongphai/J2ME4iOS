package java.util;

public abstract class Calendar {
    public static final int YEAR = 1;
    public static final int MONTH = 2;
    public static final int DATE = 5;
    public static final int DAY_OF_MONTH = 5;
    public static final int DAY_OF_WEEK = 7;
    public static final int AM_PM = 9;
    public static final int HOUR = 10;
    public static final int HOUR_OF_DAY = 11;
    public static final int MINUTE = 12;
    public static final int SECOND = 13;
    public static final int MILLISECOND = 14;
    public static final int SUNDAY = 1, MONDAY = 2, TUESDAY = 3, WEDNESDAY = 4, THURSDAY = 5, FRIDAY = 6, SATURDAY = 7;
    public static final int JANUARY = 0, FEBRUARY = 1, MARCH = 2, APRIL = 3, MAY = 4, JUNE = 5, JULY = 6, AUGUST = 7,
        SEPTEMBER = 8, OCTOBER = 9, NOVEMBER = 10, DECEMBER = 11;
    public static final int AM = 0, PM = 1;

    protected int[] fields = new int[15];
    protected boolean[] isSet = new boolean[15];
    protected long time;
    private TimeZone zone;

    protected Calendar() {
        zone = TimeZone.getDefault();
        setTimeInMillis(System.currentTimeMillis());
    }

    public static synchronized Calendar getInstance() { return new Greg(); }

    public static synchronized Calendar getInstance(TimeZone zone) {
        Calendar c = new Greg();
        c.setTimeZone(zone);
        return c;
    }

    public final Date getTime() { return new Date(getTimeInMillis()); }

    public final void setTime(Date date) { setTimeInMillis(date.getTime()); }

    protected long getTimeInMillis() { return time; }

    protected void setTimeInMillis(long millis) {
        time = millis;
        computeFields();
    }

    public final int get(int field) { return fields[field]; }

    public final void set(int field, int value) {
        fields[field] = value;
        isSet[field] = true;
        computeTime();
        computeFields();
    }

    public void setTimeZone(TimeZone value) {
        zone = value;
        computeFields();
    }

    public TimeZone getTimeZone() { return zone; }

    public boolean equals(Object obj) { return obj instanceof Calendar && ((Calendar) obj).time == time; }

    public boolean before(Object when) { return when instanceof Calendar && time < ((Calendar) when).time; }

    public boolean after(Object when) { return when instanceof Calendar && time > ((Calendar) when).time; }

    protected abstract void computeFields();

    protected abstract void computeTime();

    static final class Greg extends Calendar {
        private static long floorDiv(long a, long b) {
            long q = a / b;
            if ((a % b != 0) && ((a < 0) != (b < 0))) q--;
            return q;
        }

        /* days since 1970-01-01 for a civil date */
        static long daysFromCivil(long y, int m, int d) {
            y -= m <= 2 ? 1 : 0;
            long era = floorDiv(y, 400);
            long yoe = y - era * 400;
            long doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
            long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
            return era * 146097 + doe - 719468;
        }

        protected void computeFields() {
            long local = time + getTimeZone().getRawOffset();
            long days = floorDiv(local, 86400000L);
            long ms = local - days * 86400000L;
            long z = days + 719468;
            long era = floorDiv(z, 146097);
            long doe = z - era * 146097;
            long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
            long y = yoe + era * 400;
            long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
            long mp = (5 * doy + 2) / 153;
            int d = (int) (doy - (153 * mp + 2) / 5 + 1);
            int m = (int) (mp < 10 ? mp + 3 : mp - 9);
            if (m <= 2) y++;
            fields[YEAR] = (int) y;
            fields[MONTH] = m - 1;
            fields[DAY_OF_MONTH] = d;
            fields[DAY_OF_WEEK] = (int) (floorDiv(days + 4, 7) * -7 + days + 4) + 1;
            int secs = (int) (ms / 1000);
            fields[HOUR_OF_DAY] = secs / 3600;
            fields[HOUR] = fields[HOUR_OF_DAY] % 12;
            fields[AM_PM] = fields[HOUR_OF_DAY] < 12 ? AM : PM;
            fields[MINUTE] = (secs / 60) % 60;
            fields[SECOND] = secs % 60;
            fields[MILLISECOND] = (int) (ms % 1000);
        }

        protected void computeTime() {
            int hour = fields[HOUR_OF_DAY];
            if (isSet[HOUR] && !isSet[HOUR_OF_DAY]) hour = fields[HOUR] + (fields[AM_PM] == PM ? 12 : 0);
            isSet[HOUR] = false;
            isSet[HOUR_OF_DAY] = false;
            int month = fields[MONTH];
            long year = fields[YEAR] + floorDiv(month, 12);
            month = (int) (month - floorDiv(month, 12) * 12);
            long days = daysFromCivil(year, month + 1, 1) + fields[DAY_OF_MONTH] - 1;
            long local = days * 86400000L + hour * 3600000L + fields[MINUTE] * 60000L + fields[SECOND] * 1000L + fields[MILLISECOND];
            time = local - getTimeZone().getRawOffset();
        }
    }
}
