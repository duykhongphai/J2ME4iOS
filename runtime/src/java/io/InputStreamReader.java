package java.io;

public class InputStreamReader extends Reader {
    private InputStream in;
    private int enc; /* 0 utf8, 1 latin1, 2 utf16be, 3 utf16le, 4 cp1251 */
    private int pending = -1;

    public InputStreamReader(InputStream is) {
        in = is;
        enc = 0;
    }

    public InputStreamReader(InputStream is, String encoding) throws UnsupportedEncodingException {
        if (encoding == null) throw new NullPointerException();
        in = is;
        enc = j4i.Encoding.code(encoding);
    }

    private int readChar() throws IOException {
        if (pending >= 0) {
            int c = pending;
            pending = -1;
            return c;
        }
        int b = in.read();
        if (b < 0) return -1;
        switch (enc) {
        case 1: return b;
        case 2: {
            int b2 = in.read();
            return b2 < 0 ? -1 : (b << 8) | b2;
        }
        case 3: {
            int b2 = in.read();
            return b2 < 0 ? -1 : (b2 << 8) | b;
        }
        case 4: return j4i.Encoding.cp1251(b);
        default: {
            if (b < 0x80) return b;
            int need, cp;
            if ((b & 0xE0) == 0xC0) { need = 1; cp = b & 0x1F; }
            else if ((b & 0xF0) == 0xE0) { need = 2; cp = b & 0x0F; }
            else if ((b & 0xF8) == 0xF0) { need = 3; cp = b & 0x07; }
            else return b;
            for (int i = 0; i < need; i++) {
                int n = in.read();
                if (n < 0 || (n & 0xC0) != 0x80) return b;
                cp = (cp << 6) | (n & 0x3F);
            }
            if (cp >= 0x10000) {
                cp -= 0x10000;
                pending = 0xDC00 + (cp & 0x3FF);
                return 0xD800 + (cp >> 10);
            }
            return cp;
        }
        }
    }

    public int read() throws IOException { return readChar(); }

    public int read(char[] cbuf, int off, int len) throws IOException {
        if (len == 0) return 0;
        int n = 0;
        while (n < len) {
            int c = readChar();
            if (c < 0) break;
            cbuf[off + n++] = (char) c;
            if (in.available() <= 0 && pending < 0 && n > 0) break;
        }
        return n == 0 ? -1 : n;
    }

    public long skip(long n) throws IOException {
        long r = 0;
        while (r < n && readChar() >= 0) r++;
        return r;
    }

    public boolean ready() throws IOException { return pending >= 0 || in.available() > 0; }

    public void close() throws IOException { in.close(); }
}
