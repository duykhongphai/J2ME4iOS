package java.lang;

public final class Long {
    public static final long MIN_VALUE = 0x8000000000000000L;
    public static final long MAX_VALUE = 0x7fffffffffffffffL;

    private final long value;

    public Long(long value) { this.value = value; }

    public static String toString(long i, int radix) {
        if (radix < 2 || radix > 36) radix = 10;
        if (i == 0) return "0";
        char[] buf = new char[65];
        int pos = 64;
        boolean neg = i < 0;
        if (!neg) i = -i;
        while (i <= -radix) {
            buf[pos--] = Integer.DIGITS[(int) (-(i % radix))];
            i = i / radix;
        }
        buf[pos] = Integer.DIGITS[(int) (-i)];
        if (neg) buf[--pos] = '-';
        return new String(buf, pos, 65 - pos);
    }

    public static String toString(long i) { return toString(i, 10); }

    private static String toUnsigned(long i, int shift) {
        char[] buf = new char[64];
        int pos = 64;
        long mask = (1L << shift) - 1;
        do {
            buf[--pos] = Integer.DIGITS[(int) (i & mask)];
            i >>>= shift;
        } while (i != 0);
        return new String(buf, pos, 64 - pos);
    }

    public static String toHexString(long i) { return toUnsigned(i, 4); }

    public static String toBinaryString(long i) { return toUnsigned(i, 1); }

    public static String toOctalString(long i) { return toUnsigned(i, 3); }

    public static long parseLong(String s, int radix) throws NumberFormatException {
        if (s == null) throw new NumberFormatException("null");
        if (radix < 2 || radix > 36) throw new NumberFormatException("radix");
        long result = 0;
        boolean negative = false;
        int i = 0, max = s.length();
        long limit;
        if (max == 0) throw new NumberFormatException(s);
        char first = s.charAt(0);
        if (first == '-') {
            negative = true;
            limit = MIN_VALUE;
            i++;
        } else {
            if (first == '+') i++;
            limit = -MAX_VALUE;
        }
        if (i >= max) throw new NumberFormatException(s);
        long multmin = limit / radix;
        while (i < max) {
            int digit = Character.digit(s.charAt(i++), radix);
            if (digit < 0 || result < multmin) throw new NumberFormatException(s);
            result *= radix;
            if (result < limit + digit) throw new NumberFormatException(s);
            result -= digit;
        }
        return negative ? result : -result;
    }

    public static long parseLong(String s) throws NumberFormatException { return parseLong(s, 10); }

    public static Long valueOf(String s) throws NumberFormatException { return new Long(parseLong(s, 10)); }

    public static Long valueOf(long l) { return new Long(l); }

    public int intValue() { return (int) value; }

    public long longValue() { return value; }

    public float floatValue() { return value; }

    public double doubleValue() { return value; }

    public String toString() { return toString(value); }

    public int hashCode() { return (int) (value ^ (value >>> 32)); }

    public boolean equals(Object obj) { return obj instanceof Long && ((Long) obj).value == value; }
}
