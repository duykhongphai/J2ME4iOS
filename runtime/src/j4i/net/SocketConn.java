package j4i.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.microedition.io.SocketConnection;

public final class SocketConn implements SocketConnection {
    private final Sock s;
    private final int[] opts = new int[5];
    private boolean closed;

    public SocketConn(String host, int port) throws IOException { s = Sock.connect(host, port); }

    private void check() throws IOException {
        if (closed) throw new IOException("Connection closed");
    }

    public void setSocketOption(byte option, int value) throws IOException {
        if (option < 0 || option > 4 || value < 0) throw new IllegalArgumentException();
        check();
        opts[option] = value;
    }

    public int getSocketOption(byte option) throws IOException {
        if (option < 0 || option > 4) throw new IllegalArgumentException();
        check();
        return opts[option];
    }

    private static String hostPart(String a) {
        if (a == null) return null;
        int i = a.lastIndexOf(':');
        return i < 0 ? a : a.substring(0, i);
    }

    private static int portPart(String a) {
        if (a == null) return 0;
        int i = a.lastIndexOf(':');
        try {
            return i < 0 ? 0 : Integer.parseInt(a.substring(i + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getLocalAddress() throws IOException {
        check();
        return hostPart(s.address(true));
    }

    public int getLocalPort() throws IOException {
        check();
        return portPart(s.address(true));
    }

    public String getAddress() throws IOException {
        check();
        return s.host;
    }

    public int getPort() throws IOException {
        check();
        return s.port;
    }

    public InputStream openInputStream() throws IOException {
        check();
        return s.input();
    }

    public DataInputStream openDataInputStream() throws IOException { return new DataInputStream(openInputStream()); }

    public OutputStream openOutputStream() throws IOException {
        check();
        return s.output();
    }

    public DataOutputStream openDataOutputStream() throws IOException { return new DataOutputStream(openOutputStream()); }

    public void close() {
        closed = true;
        s.close();
    }
}
