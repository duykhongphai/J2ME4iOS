package java.util;

public abstract class TimeZone {
    private static TimeZone defaultZone;

    public TimeZone() {}

    public abstract int getOffset(int era, int year, int month, int day, int dayOfWeek, int millis);

    public abstract int getRawOffset();

    public abstract boolean useDaylightTime();

    public String getID() { return "GMT"; }

    public static synchronized TimeZone getTimeZone(String id) {
        if (id == null) throw new NullPointerException();
        if (id.equals("GMT") || id.equals("UTC")) return new Simple("GMT", 0);
        if (id.startsWith("GMT") && id.length() > 4) {
            try {
                int sign = id.charAt(3) == '-' ? -1 : 1;
                String rest = id.substring(4);
                int colon = rest.indexOf(':');
                int h = Integer.parseInt(colon < 0 ? rest : rest.substring(0, colon));
                int m = colon < 0 ? 0 : Integer.parseInt(rest.substring(colon + 1));
                return new Simple(id, sign * (h * 3600000 + m * 60000));
            } catch (Exception e) {
            }
        }
        return getDefault();
    }

    public static synchronized TimeZone getDefault() {
        if (defaultZone == null) {
            int off = j4i.Sys.tzOffset();
            int h = Math.abs(off) / 3600000, m = (Math.abs(off) / 60000) % 60;
            String id = off == 0 ? "GMT" : "GMT" + (off < 0 ? "-" : "+") + (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
            defaultZone = new Simple(id, off);
        }
        return defaultZone;
    }

    public static String[] getAvailableIDs() { return new String[] {"GMT", getDefault().getID()}; }

    static final class Simple extends TimeZone {
        private final String id;
        private final int offset;

        Simple(String id, int offset) {
            this.id = id;
            this.offset = offset;
        }

        public int getOffset(int era, int year, int month, int day, int dayOfWeek, int millis) { return offset; }

        public int getRawOffset() { return offset; }

        public boolean useDaylightTime() { return false; }

        public String getID() { return id; }
    }
}
