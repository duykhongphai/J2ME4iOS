package j4i;

import java.io.UnsupportedEncodingException;

/** Character encodings: UTF-8 (default, lenient), ISO-8859-1, UTF-16BE/LE, windows-1251. */
public final class Encoding {
    private Encoding() {}

    private static final char[] CP1251 = (
        "ЂЃ‚ѓ„…†‡€‰Љ‹ЊЌЋЏ"
        + "ђ‘’“”•–—\u0098™љ›њќћџ"
        + " ЎўЈ¤Ґ¦§Ё©Є«¬­®Ї"
        + "°±Ііґµ¶·ё№є»јЅѕї").toCharArray();

    public static int cp1251(int b) {
        if (b < 0x80) return b;
        if (b < 0xC0) return CP1251[b - 0x80];
        return 0x410 + (b - 0xC0);
    }

    public static int code(String enc) throws UnsupportedEncodingException {
        if (enc == null) return 0;
        String e = enc.toUpperCase();
        if (e.equals("UTF-8") || e.equals("UTF8")) return 0;
        if (e.equals("ISO-8859-1") || e.equals("ISO8859_1") || e.equals("ISO8859-1") || e.equals("LATIN1")
            || e.equals("US-ASCII") || e.equals("ASCII") || e.equals("CP1252") || e.equals("WINDOWS-1252")) return 1;
        if (e.equals("UTF-16BE") || e.equals("UTF-16") || e.equals("UTF16") || e.equals("UNICODEBIGUNMARKED")
            || e.equals("UCS-2")) return 2;
        if (e.equals("UTF-16LE") || e.equals("UNICODELITTLEUNMARKED")) return 3;
        if (e.equals("CP1251") || e.equals("WINDOWS-1251")) return 4;
        throw new UnsupportedEncodingException(enc);
    }

    public static char[] decode(byte[] b, int off, int len, String enc) {
        int c;
        try {
            c = code(enc);
        } catch (UnsupportedEncodingException e) {
            c = 0;
        }
        char[] out;
        switch (c) {
        case 1:
            out = new char[len];
            for (int i = 0; i < len; i++) out[i] = (char) (b[off + i] & 0xff);
            return out;
        case 4:
            out = new char[len];
            for (int i = 0; i < len; i++) out[i] = (char) cp1251(b[off + i] & 0xff);
            return out;
        case 2:
        case 3: {
            int n = len / 2, s = 0;
            if (len >= 2 && c == 2 && (b[off] & 0xff) == 0xFE && (b[off + 1] & 0xff) == 0xFF) s = 1;
            out = new char[n - s];
            for (int i = s; i < n; i++) {
                int hi = b[off + i * 2] & 0xff, lo = b[off + i * 2 + 1] & 0xff;
                out[i - s] = (char) (c == 2 ? (hi << 8) | lo : (lo << 8) | hi);
            }
            return out;
        }
        default:
            return Utf8.decode(b, off, len);
        }
    }

    public static byte[] encode(char[] s, int off, int len, String enc) {
        int c;
        try {
            c = code(enc);
        } catch (UnsupportedEncodingException e) {
            c = 0;
        }
        byte[] out;
        switch (c) {
        case 1:
            out = new byte[len];
            for (int i = 0; i < len; i++) {
                char ch = s[off + i];
                out[i] = (byte) (ch < 256 ? ch : '?');
            }
            return out;
        case 4:
            out = new byte[len];
            for (int i = 0; i < len; i++) {
                char ch = s[off + i];
                int v = '?';
                if (ch < 0x80) v = ch;
                else if (ch >= 0x410 && ch <= 0x44F) v = ch - 0x410 + 0xC0;
                else for (int k = 0; k < 64; k++) if (CP1251[k] == ch) { v = 0x80 + k; break; }
                out[i] = (byte) v;
            }
            return out;
        case 2:
        case 3:
            out = new byte[len * 2];
            for (int i = 0; i < len; i++) {
                char ch = s[off + i];
                byte hi = (byte) (ch >> 8), lo = (byte) ch;
                out[i * 2] = c == 2 ? hi : lo;
                out[i * 2 + 1] = c == 2 ? lo : hi;
            }
            return out;
        default:
            return Utf8.encode(s, off, len);
        }
    }
}
