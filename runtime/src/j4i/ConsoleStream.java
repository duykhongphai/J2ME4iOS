package j4i;

import java.io.OutputStream;

/** System.out / System.err: line buffered into the host log. */
public final class ConsoleStream extends OutputStream {
    private final boolean err;
    private byte[] buf = new byte[256];
    private int n;

    public ConsoleStream(boolean err) { this.err = err; }

    public synchronized void write(int b) {
        if (b == '\n') {
            Sys.logBytes(buf, n, err);
            n = 0;
            return;
        }
        if (n == buf.length) {
            byte[] nb = new byte[buf.length * 2];
            System.arraycopy(buf, 0, nb, 0, n);
            buf = nb;
        }
        buf[n++] = (byte) b;
    }

    public synchronized void write(byte[] b, int off, int len) {
        for (int i = 0; i < len; i++) write(b[off + i]);
    }

    public synchronized void flush() {
        if (n > 0) {
            Sys.logBytes(buf, n, err);
            n = 0;
        }
    }
}
