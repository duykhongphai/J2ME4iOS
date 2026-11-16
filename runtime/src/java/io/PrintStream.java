package java.io;

public class PrintStream extends OutputStream {
    private OutputStream out;
    private boolean trouble;

    public PrintStream(OutputStream out) {
        if (out == null) throw new NullPointerException();
        this.out = out;
    }

    public void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            trouble = true;
        }
    }

    public void close() {
        try {
            out.close();
        } catch (IOException e) {
            trouble = true;
        }
    }

    public boolean checkError() {
        flush();
        return trouble;
    }

    protected void setError() { trouble = true; }

    public void write(int b) {
        try {
            out.write(b);
            if (b == '\n') out.flush();
        } catch (IOException e) {
            trouble = true;
        }
    }

    public void write(byte[] buf, int off, int len) {
        try {
            out.write(buf, off, len);
        } catch (IOException e) {
            trouble = true;
        }
    }

    private void writeStr(String s) {
        if (s == null) s = "null";
        byte[] b = s.getBytes();
        write(b, 0, b.length);
    }

    public void print(boolean b) { writeStr(b ? "true" : "false"); }

    public void print(char c) { writeStr(String.valueOf(c)); }

    public void print(int i) { writeStr(String.valueOf(i)); }

    public void print(long l) { writeStr(String.valueOf(l)); }

    public void print(float f) { writeStr(String.valueOf(f)); }

    public void print(double d) { writeStr(String.valueOf(d)); }

    public void print(char[] s) { writeStr(new String(s)); }

    public void print(String s) { writeStr(s); }

    public void print(Object obj) { writeStr(String.valueOf(obj)); }

    public void println() {
        write('\n');
    }

    public void println(boolean x) { print(x); println(); }

    public void println(char x) { print(x); println(); }

    public void println(int x) { print(x); println(); }

    public void println(long x) { print(x); println(); }

    public void println(float x) { print(x); println(); }

    public void println(double x) { print(x); println(); }

    public void println(char[] x) { print(x); println(); }

    public void println(String x) { print(x); println(); }

    public void println(Object x) { print(x); println(); }
}
