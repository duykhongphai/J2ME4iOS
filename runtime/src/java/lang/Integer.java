package java.lang;

public final class Integer {
    public static final int MIN_VALUE = 0x80000000;
    public static final int MAX_VALUE = 0x7fffffff;

    private final int value;

    public Integer(int value) { this.value = value; }

    static final char[] DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
        'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
    };

    public static String toString(int i, int radix) {
        if (radix < 2 || radix > 36) radix = 10;
        if (i == 0) return "0";
        char[] buf = new char[33];
        boolean neg = i < 0;
        int pos = 32;
        if (!neg) i = -i;
        while (i <= -radix) {
            buf[pos--] = DIGITS[-(i % radix)];
            i = i / radix;
        }
        buf[pos] = DIGITS[-i];
        if (neg) buf[--pos] = '-';
        return new String(buf, pos, 33 - pos);
    }

    private static String toUnsigned(int i, int shift) {
        char[] buf = new char[32];
        int pos = 32;
        int radix = 1 << shift;
        int mask = radix - 1;
        do {
            buf[--pos] = DIGITS[i & mask];
            i >>>= shift;
        } while (i != 0);
        return new String(buf, pos, 32 - pos);
    }

    public static String toHexString(int i) { return toUnsigned(i, 4); }

    public static String toOctalString(int i) { return toUnsigned(i, 3); }

    public static String toBinaryString(int i) { return toUnsigned(i, 1); }

    public static String toString(int i) { return toString(i, 10); }

    public static int parseInt(String s, int radix) throws NumberFormatException {
        if (s == null) throw new NumberFormatException("null");
        if (radix < 2 || radix > 36) throw new NumberFormatException("radix");
        int result = 0;
        boolean negative = false;
        int i = 0, max = s.length();
        int limit;
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
        int multmin = limit / radix;
        while (i < max) {
            int digit = Character.digit(s.charAt(i++), radix);
            if (digit < 0 || result < multmin) throw new NumberFormatException(s);
            result *= radix;
            if (result < limit + digit) throw new NumberFormatException(s);
            result -= digit;
        }
        return negative ? result : -result;
    }

    public static int parseInt(String s) throws NumberFormatException { return parseInt(s, 10); }

    public static Integer valueOf(String s, int radix) throws NumberFormatException { return new Integer(parseInt(s, radix)); }

    public static Integer valueOf(String s) throws NumberFormatException { return new Integer(parseInt(s, 10)); }

    public static Integer valueOf(int i) { return new Integer(i); }

    public byte byteValue() { return (byte) value; }

    public short shortValue() { return (short) value; }

    public int intValue() { return value; }

    public long longValue() { return value; }

    public float floatValue() { return value; }

    public double doubleValue() { return value; }

    public String toString() { return toString(value); }

    public int hashCode() { return value; }

    public boolean equals(Object obj) { return obj instanceof Integer && ((Integer) obj).value == value; }
}
