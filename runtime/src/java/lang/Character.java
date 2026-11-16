package java.lang;

public final class Character {
    public static final int MIN_RADIX = 2;
    public static final int MAX_RADIX = 36;
    public static final char MIN_VALUE = '\u0000';
    public static final char MAX_VALUE = '￿';

    private final char value;

    public Character(char value) { this.value = value; }

    public char charValue() { return value; }

    public int hashCode() { return value; }

    public boolean equals(Object obj) { return obj instanceof Character && ((Character) obj).value == value; }

    public String toString() { return String.valueOf(value); }

    public static Character valueOf(char c) { return new Character(c); }

    public static boolean isLowerCase(char ch) { return toUpperCase(ch) != ch || ch == 'ß'; }

    public static boolean isUpperCase(char ch) { return toLowerCase(ch) != ch; }

    public static boolean isDigit(char ch) { return (ch >= '0' && ch <= '9') || (ch >= '٠' && ch <= '٩') || (ch >= '０' && ch <= '９'); }

    public static boolean isLetter(char ch) {
        if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) return true;
        if (ch < 0xC0) return ch == 0xAA || ch == 0xB5 || ch == 0xBA;
        if (ch == 0xD7 || ch == 0xF7) return false;
        return ch < 0x2000 || (ch >= 0x3040 && ch < 0xD800) || (ch >= 0xF900 && ch < 0xFF00);
    }

    public static boolean isLetterOrDigit(char ch) { return isLetter(ch) || isDigit(ch); }

    public static boolean isSpace(char ch) { return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' || ch == '\f'; }

    public static boolean isWhitespace(char ch) { return isSpace(ch) || ch == 0x0B || (ch >= 0x1C && ch <= 0x1F); }

    public static char toLowerCase(char c) {
        if (c < 'A') return c;
        if (c <= 'Z') return (char) (c + 32);
        if (c < 0xC0) return c;
        if (c <= 0xDE) return c == 0xD7 ? c : (char) (c + 32);
        if (c >= 0x100 && c <= 0x17F) {
            if (c == 0x130) return 'i';
            if ((c >= 0x139 && c <= 0x148) || (c >= 0x179 && c <= 0x17E)) return (c & 1) == 1 ? (char) (c + 1) : c;
            if (c == 0x178) return 0xFF;
            return (c & 1) == 0 ? (char) (c + 1) : c;
        }
        if (c >= 0x1A0 && c <= 0x1B0) return (c == 0x1A0 || c == 0x1AF) ? (char) (c + 1) : c;
        if (c == 0x110) return 0x111;
        if (c >= 0x391 && c <= 0x3AB && c != 0x3A2) return (char) (c + 32);
        if (c >= 0x410 && c <= 0x42F) return (char) (c + 32);
        if (c >= 0x400 && c <= 0x40F) return (char) (c + 80);
        if (c >= 0x460 && c <= 0x4FF) return (c & 1) == 0 ? (char) (c + 1) : c;
        if (c >= 0x1E00 && c <= 0x1EFF) return (c & 1) == 0 ? (char) (c + 1) : c;
        if (c >= 0xFF21 && c <= 0xFF3A) return (char) (c + 32);
        return c;
    }

    public static char toUpperCase(char c) {
        if (c < 'a') return c;
        if (c <= 'z') return (char) (c - 32);
        if (c < 0xE0) return c == 0xB5 ? (char) 0x39C : c;
        if (c <= 0xFE) return c == 0xF7 ? c : (char) (c - 32);
        if (c == 0xFF) return 0x178;
        if (c >= 0x100 && c <= 0x17F) {
            if (c == 0x131) return 'I';
            if ((c >= 0x139 && c <= 0x148) || (c >= 0x179 && c <= 0x17E)) return (c & 1) == 0 ? (char) (c - 1) : c;
            return (c & 1) == 1 ? (char) (c - 1) : c;
        }
        if (c == 0x1A1 || c == 0x1B0) return (char) (c - 1);
        if (c == 0x111) return 0x110;
        if (c >= 0x3B1 && c <= 0x3CB && c != 0x3C2) return (char) (c - 32);
        if (c >= 0x430 && c <= 0x44F) return (char) (c - 32);
        if (c >= 0x450 && c <= 0x45F) return (char) (c - 80);
        if (c >= 0x460 && c <= 0x4FF) return (c & 1) == 1 ? (char) (c - 1) : c;
        if (c >= 0x1E00 && c <= 0x1EFF) return (c & 1) == 1 ? (char) (c - 1) : c;
        if (c >= 0xFF41 && c <= 0xFF5A) return (char) (c - 32);
        return c;
    }

    public static int digit(char ch, int radix) {
        int v;
        if (ch >= '0' && ch <= '9') v = ch - '0';
        else if (ch >= 'a' && ch <= 'z') v = ch - 'a' + 10;
        else if (ch >= 'A' && ch <= 'Z') v = ch - 'A' + 10;
        else if (ch >= '０' && ch <= '９') v = ch - '０';
        else return -1;
        return v < radix ? v : -1;
    }

    public static char forDigit(int digit, int radix) {
        if (digit >= radix || digit < 0 || radix < MIN_RADIX || radix > MAX_RADIX) return 0;
        return digit < 10 ? (char) ('0' + digit) : (char) ('a' - 10 + digit);
    }
}
