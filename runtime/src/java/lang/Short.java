package java.lang;

public final class Short {
    public static final short MIN_VALUE = -32768;
    public static final short MAX_VALUE = 32767;

    private final short value;

    public Short(short value) { this.value = value; }

    public static short parseShort(String s, int radix) throws NumberFormatException {
        int i = Integer.parseInt(s, radix);
        if (i < MIN_VALUE || i > MAX_VALUE) throw new NumberFormatException(s);
        return (short) i;
    }

    public static short parseShort(String s) throws NumberFormatException { return parseShort(s, 10); }

    public static Short valueOf(short v) { return new Short(v); }

    public static Short valueOf(String s) throws NumberFormatException { return new Short(parseShort(s, 10)); }

    public byte byteValue() { return (byte) value; }

    public short shortValue() { return (short) value; }

    public int intValue() { return value; }

    public long longValue() { return value; }

    public float floatValue() { return value; }

    public double doubleValue() { return value; }


    public String toString() { return Integer.toString(value); }

    public static String toString(short v) { return Integer.toString(v); }

    public int hashCode() { return value; }

    public boolean equals(Object obj) { return obj instanceof Short && ((Short) obj).value == value; }
}
