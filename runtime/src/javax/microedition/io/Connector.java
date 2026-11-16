package javax.microedition.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Supported schemes: http (https fails at connect time), socket, file (JSR-75, sandboxed),
 * sms/mms/cbs (messages cannot be sent), resource (read-only jar resources).
 */
public class Connector {
    public static final int READ = 1;
    public static final int WRITE = 2;
    public static final int READ_WRITE = 3;

    private Connector() {}

    public static Connection open(String name) throws IOException { return open(name, READ_WRITE, false); }

    public static Connection open(String name, int mode) throws IOException { return open(name, mode, false); }

    public static Connection open(String name, int mode, boolean timeouts) throws IOException {
        if (name == null) throw new IllegalArgumentException("null URL");
        if (mode != READ && mode != WRITE && mode != READ_WRITE) throw new IllegalArgumentException("mode");
        int c = name.indexOf(':');
        if (c <= 0) throw new IllegalArgumentException("Invalid URL: " + name);
        String scheme = name.substring(0, c).toLowerCase();
        if (scheme.equals("http") || scheme.equals("https")) return new j4i.net.HttpConn(name);
        if (scheme.equals("file")) return new j4i.net.FileConn(name, mode);
        if (scheme.equals("sms") || scheme.equals("mms") || scheme.equals("cbs")) return new j4i.net.SmsConn(name);
        if (scheme.equals("socket")) {
            String hp = name.substring(c + 1);
            if (!hp.startsWith("//")) throw new IllegalArgumentException(name);
            hp = hp.substring(2);
            int semi = hp.indexOf(';');
            if (semi >= 0) hp = hp.substring(0, semi);
            int pc = hp.lastIndexOf(':');
            if (pc < 0) throw new IllegalArgumentException("Missing port: " + name);
            String host = hp.substring(0, pc);
            int port = Integer.parseInt(hp.substring(pc + 1));
            if (host.length() == 0) throw new ConnectionNotFoundException("Server sockets are not supported");
            return new j4i.net.SocketConn(host, port);
        }
        if (scheme.equals("resource")) return new j4i.net.ResourceConn(name);
        System.out.println("Connector.open not supported: " + name);
        throw new ConnectionNotFoundException("Not supported: " + name);
    }

    public static DataInputStream openDataInputStream(String name) throws IOException { return new DataInputStream(openInputStream(name)); }

    public static DataOutputStream openDataOutputStream(String name) throws IOException { return new DataOutputStream(openOutputStream(name)); }

    public static InputStream openInputStream(String name) throws IOException {
        Connection c = open(name, READ);
        if (!(c instanceof InputConnection)) {
            c.close();
            throw new IllegalArgumentException("Not an input connection: " + name);
        }
        return ((InputConnection) c).openInputStream();
    }

    public static OutputStream openOutputStream(String name) throws IOException {
        Connection c = open(name, WRITE);
        if (!(c instanceof OutputConnection)) {
            c.close();
            throw new IllegalArgumentException("Not an output connection: " + name);
        }
        return ((OutputConnection) c).openOutputStream();
    }
}
