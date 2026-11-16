package j4i;

/** Native UTF-8 conversion (lenient: invalid bytes map to Latin-1). */
public final class Utf8 {
    private Utf8() {}

    public static native char[] decode(byte[] b, int off, int len);

    public static native byte[] encode(char[] s, int off, int len);
}
