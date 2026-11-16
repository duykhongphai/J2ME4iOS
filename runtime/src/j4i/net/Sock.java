package j4i.net;

import j4i.Sys;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;

/** A TCP connection driven by the VM's non-blocking socket natives. */
public final class Sock {
    static final int CONNECT_TIMEOUT = 30000;

    final String host;
    final int port;
    private int h;
    private InputStream in;
    private OutputStream out;
    int readTimeout = 60000;

    private Sock(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static Sock connect(String host, int port) throws IOException {
        if (host == null || host.length() == 0) throw new IllegalArgumentException("host");
        if (port <= 0 || port > 65535) throw new IllegalArgumentException("port " + port);
        Sock s = new Sock(host, port);
        s.h = Sys.netConnect(host, port);
        if (s.h <= 0) throw new IOException("Too many connections");
        long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT;
        for (;;) {
            int st = Sys.netState(s.h);
            if (st == 1) break;
            if (st < 0) {
                Sys.netClose(s.h);
                s.h = 0;
                throw new javax.microedition.io.ConnectionNotFoundException("Cannot connect to " + host + ":" + port);
            }
            if (System.currentTimeMillis() > deadline) {
                Sys.netClose(s.h);
                s.h = 0;
                throw new InterruptedIOException("Connect timeout " + host + ":" + port);
            }
            nap();
        }
        return s;
    }

    static void nap() throws InterruptedIOException {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            throw new InterruptedIOException();
        }
    }

    int handle() throws IOException {
        if (h <= 0) throw new IOException("Connection closed");
        return h;
    }

    public synchronized InputStream input() {
        if (in == null) in = new In();
        return in;
    }

    public synchronized OutputStream output() {
        if (out == null) out = new Out();
        return out;
    }

    public void close() {
        if (h > 0) Sys.netClose(h);
        h = 0;
    }

    public String address(boolean local) {
        return h > 0 ? Sys.netAddress(h, local) : null;
    }

    final class In extends InputStream {
        private final byte[] one = new byte[1];
        private boolean eof;

        public int read() throws IOException {
            int n = read(one, 0, 1);
            return n <= 0 ? -1 : (one[0] & 0xFF);
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) throw new NullPointerException();
            if (off < 0 || len < 0 || off + len > b.length) throw new IndexOutOfBoundsException();
            if (len == 0) return 0;
            if (eof) return -1;
            long deadline = System.currentTimeMillis() + readTimeout;
            for (;;) {
                int n = Sys.netRead(handle(), b, off, len);
                if (n > 0) return n;
                if (n == -1) {
                    eof = true;
                    return -1;
                }
                if (n < -1) throw new IOException("Connection reset");
                if (System.currentTimeMillis() > deadline) throw new InterruptedIOException("Read timeout");
                nap();
            }
        }

        public int available() throws IOException { return h > 0 ? Sys.netAvailable(h) : 0; }

        public void close() {}
    }

    final class Out extends OutputStream {
        private final byte[] one = new byte[1];

        public void write(int b) throws IOException {
            one[0] = (byte) b;
            write(one, 0, 1);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            if (b == null) throw new NullPointerException();
            if (off < 0 || len < 0 || off + len > b.length) throw new IndexOutOfBoundsException();
            if (len == 0) return;
            if (Sys.netWrite(handle(), b, off, len) != len) throw new IOException("Write failed");
        }

        public void close() {}
    }
}
