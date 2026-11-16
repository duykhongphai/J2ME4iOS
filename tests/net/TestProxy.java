import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tiny SOCKS5 (with user/pass "u"/"p") + HTTP CONNECT proxy for tests (host JVM).
 * Usage: java TestProxy socksPort httpPort countFile
 * Writes the number of tunnelled connections to countFile after each one.
 */
public class TestProxy {
    static final AtomicInteger count = new AtomicInteger();
    static String countFile;

    public static void main(String[] a) throws Exception {
        countFile = a[2];
        if (a.length > 3) { // safety: exit after the given number of seconds
            final long ms = Long.parseLong(a[3]) * 1000;
            Thread t = new Thread(() -> { try { Thread.sleep(ms); } catch (InterruptedException e) {} System.exit(0); });
            t.setDaemon(true);
            t.start();
        }
        bump(0);
        ServerSocket socks = new ServerSocket(Integer.parseInt(a[0]), 50, InetAddress.getLoopbackAddress());
        ServerSocket http = new ServerSocket(Integer.parseInt(a[1]), 50, InetAddress.getLoopbackAddress());
        new Thread(() -> accept(socks, true)).start();
        accept(http, false);
    }

    static synchronized void bump(int d) throws IOException {
        int n = count.addAndGet(d);
        try (OutputStream o = new java.io.FileOutputStream(countFile)) {
            o.write(String.valueOf(n).getBytes());
        }
    }

    static void accept(ServerSocket ss, boolean socks) {
        while (true) {
            try {
                Socket c = ss.accept();
                new Thread(() -> {
                    try {
                        if (socks) socks(c);
                        else http(c);
                    } catch (Exception e) {
                        try { c.close(); } catch (IOException ignored) {}
                    }
                }).start();
            } catch (IOException e) {
                return;
            }
        }
    }

    static byte[] read(InputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        int o = 0;
        while (o < n) {
            int r = in.read(b, o, n - o);
            if (r < 0) throw new IOException("eof");
            o += r;
        }
        return b;
    }

    static void socks(Socket c) throws IOException {
        InputStream in = c.getInputStream();
        OutputStream out = c.getOutputStream();
        byte[] g = read(in, 2);
        byte[] methods = read(in, g[1]);
        boolean userpass = false;
        for (byte m : methods) if (m == 2) userpass = true;
        if (!userpass) { out.write(new byte[] {5, (byte) 0xFF}); c.close(); return; }
        out.write(new byte[] {5, 2});
        read(in, 1);
        String user = new String(read(in, read(in, 1)[0] & 255));
        String pass = new String(read(in, read(in, 1)[0] & 255));
        boolean ok = user.equals("u") && pass.equals("p");
        out.write(new byte[] {1, (byte) (ok ? 0 : 1)});
        if (!ok) { c.close(); return; }
        byte[] h = read(in, 4);
        String host;
        if (h[3] == 1) host = InetAddress.getByAddress(read(in, 4)).getHostAddress();
        else if (h[3] == 4) host = InetAddress.getByAddress(read(in, 16)).getHostAddress();
        else host = new String(read(in, read(in, 1)[0] & 255));
        byte[] p = read(in, 2);
        int port = ((p[0] & 255) << 8) | (p[1] & 255);
        Socket up = new Socket(host, port);
        out.write(new byte[] {5, 0, 0, 1, 0, 0, 0, 0, 0, 0});
        bump(1);
        pipe(c, up);
    }

    static void http(Socket c) throws IOException {
        InputStream in = c.getInputStream();
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) throw new IOException("eof");
            head.write(b);
            byte[] h = head.toByteArray();
            int n = h.length;
            if (n >= 4 && h[n - 4] == '\r' && h[n - 3] == '\n' && h[n - 2] == '\r' && h[n - 1] == '\n') break;
        }
        String first = head.toString().split("\r\n")[0];
        String[] parts = first.split(" ");
        if (!parts[0].equals("CONNECT")) throw new IOException("not CONNECT");
        String hp = parts[1];
        int colon = hp.lastIndexOf(':');
        Socket up = new Socket(hp.substring(0, colon), Integer.parseInt(hp.substring(colon + 1)));
        c.getOutputStream().write("HTTP/1.1 200 Connection established\r\n\r\n".getBytes());
        bump(1);
        pipe(c, up);
    }

    static void pipe(Socket a, Socket b) {
        Thread t1 = new Thread(() -> copy(a, b)), t2 = new Thread(() -> copy(b, a));
        t1.start();
        t2.start();
    }

    static void copy(Socket from, Socket to) {
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (IOException ignored) {
        } finally {
            try { from.close(); } catch (IOException ignored) {}
            try { to.close(); } catch (IOException ignored) {}
        }
    }
}
