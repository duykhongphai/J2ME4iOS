package j4i.net;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.HttpsConnection;
import javax.microedition.io.SecurityInfo;

/** HTTP/1.1 client (one request per connection) on top of Sock. https is not supported. */
public final class HttpConn implements HttpsConnection {
    private static final int SETUP = 0, CONNECTED = 1, CLOSED = 2;

    private final String url, protocol, host, file, query, ref;
    private final int port;
    private int state = SETUP;
    private String method = GET;
    private final Vector reqKeys = new Vector(), reqVals = new Vector();
    private ReqOut body;
    private Sock sock;
    private int code = -1;
    private String message;
    private final Vector resKeys = new Vector(), resVals = new Vector();
    private InputStream bodyIn;
    private boolean inOpened, outOpened;

    public HttpConn(String url) throws IOException {
        this.url = url;
        int ps = url.indexOf("://");
        protocol = url.substring(0, ps).toLowerCase();
        if (!protocol.equals("http") && !protocol.equals("https")) throw new IllegalArgumentException(url);
        String rest = url.substring(ps + 3);
        int hashI = rest.indexOf('#');
        ref = hashI >= 0 ? rest.substring(hashI + 1) : null;
        if (hashI >= 0) rest = rest.substring(0, hashI);
        int slash = rest.indexOf('/');
        int qm = rest.indexOf('?');
        int end = slash;
        if (end < 0 || (qm >= 0 && qm < end)) end = qm;
        String hp = end < 0 ? rest : rest.substring(0, end);
        String path = end < 0 ? "/" : rest.substring(end);
        if (path.startsWith("?")) path = "/" + path;
        int at = hp.lastIndexOf('@');
        if (at >= 0) hp = hp.substring(at + 1);
        int colon = hp.lastIndexOf(':');
        int defPort = protocol.equals("https") ? 443 : 80;
        if (colon >= 0 && hp.indexOf(']') < colon) {
            String p = hp.substring(colon + 1);
            port = p.length() == 0 ? defPort : Integer.parseInt(p);
            hp = hp.substring(0, colon);
        } else port = defPort;
        if (hp.startsWith("[") && hp.endsWith("]")) hp = hp.substring(1, hp.length() - 1);
        if (hp.length() == 0) throw new IllegalArgumentException("Missing host: " + url);
        host = hp;
        int q = path.indexOf('?');
        file = q >= 0 ? path.substring(0, q) : path;
        query = q >= 0 ? path.substring(q + 1) : null;
    }

    private void checkSetup() throws IOException {
        if (state == CLOSED) throw new IOException("Connection closed");
        if (state != SETUP) throw new IOException("Already connected");
    }

    public String getURL() { return url; }

    public String getProtocol() { return protocol; }

    public String getHost() { return host; }

    public String getFile() { return file; }

    public String getRef() { return ref; }

    public String getQuery() { return query; }

    public int getPort() { return port; }

    public String getRequestMethod() { return method; }

    public void setRequestMethod(String m) throws IOException {
        checkSetup();
        if (outOpened) throw new IOException("Output stream already opened");
        if (!GET.equals(m) && !POST.equals(m) && !HEAD.equals(m)) throw new IOException("Unsupported method " + m);
        method = m;
    }

    private int findReq(String key) {
        for (int i = 0; i < reqKeys.size(); i++)
            if (((String) reqKeys.elementAt(i)).equalsIgnoreCase(key)) return i;
        return -1;
    }

    public String getRequestProperty(String key) {
        int i = findReq(key);
        return i < 0 ? null : (String) reqVals.elementAt(i);
    }

    public void setRequestProperty(String key, String value) throws IOException {
        checkSetup();
        if (key == null) throw new NullPointerException();
        int i = findReq(key);
        if (i >= 0) reqVals.setElementAt(value, i);
        else {
            reqKeys.addElement(key);
            reqVals.addElement(value);
        }
    }

    final class ReqOut extends ByteArrayOutputStream {
        public void flush() throws IOException {
            if (state == SETUP) connect();
        }

        public void close() throws IOException {
            if (state == SETUP) connect();
        }
    }

    private static void writeAscii(OutputStream o, String s) throws IOException {
        byte[] b = new byte[s.length()];
        for (int i = 0; i < b.length; i++) b[i] = (byte) s.charAt(i);
        o.write(b, 0, b.length);
    }

    private void connect() throws IOException {
        if (state == CONNECTED) return;
        if (state == CLOSED) throw new IOException("Connection closed");
        if (protocol.equals("https")) throw new IOException("HTTPS is not supported: " + url);
        sock = Sock.connect(host, port);
        state = CONNECTED;
        byte[] payload = body != null ? body.toByteArray() : null;
        StringBuffer sb = new StringBuffer();
        sb.append(method).append(' ').append(file);
        if (query != null) sb.append('?').append(query);
        sb.append(" HTTP/1.1\r\n");
        if (findReq("Host") < 0) sb.append("Host: ").append(host).append(port != 80 ? ":" + port : "").append("\r\n");
        if (findReq("User-Agent") < 0) {
            String ua = j4i.Sys.getProperty("microedition.platform");
            sb.append("User-Agent: ").append(ua != null ? ua : "J2ME4iOS").append(" Profile/MIDP-2.0 Configuration/CLDC-1.1\r\n");
        }
        for (int i = 0; i < reqKeys.size(); i++) {
            String k = (String) reqKeys.elementAt(i);
            if (k.equalsIgnoreCase("Connection") || k.equalsIgnoreCase("Content-Length")) continue;
            sb.append(k).append(": ").append(reqVals.elementAt(i)).append("\r\n");
        }
        sb.append("Connection: close\r\n");
        if (payload != null || POST.equals(method)) sb.append("Content-Length: ").append(payload != null ? payload.length : 0).append("\r\n");
        sb.append("\r\n");
        OutputStream o = sock.output();
        writeAscii(o, sb.toString());
        if (payload != null && payload.length > 0) o.write(payload, 0, payload.length);
        readResponseHead();
    }

    private String readLine(InputStream in) throws IOException {
        StringBuffer sb = new StringBuffer();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 16384) throw new IOException("Header line too long");
        }
        if (c < 0 && sb.length() == 0) return null;
        return sb.toString();
    }

    private void readResponseHead() throws IOException {
        InputStream in = new BufferedIn(sock.input());
        for (;;) {
            String status = readLine(in);
            if (status == null) throw new IOException("Empty response");
            int sp = status.indexOf(' ');
            if (!status.startsWith("HTTP/") || sp < 0) throw new IOException("Bad status line: " + status);
            int sp2 = status.indexOf(' ', sp + 1);
            String c = sp2 < 0 ? status.substring(sp + 1) : status.substring(sp + 1, sp2);
            code = Integer.parseInt(c.trim());
            message = sp2 < 0 ? "" : status.substring(sp2 + 1);
            resKeys.removeAllElements();
            resVals.removeAllElements();
            String line;
            while ((line = readLine(in)) != null && line.length() > 0) {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                resKeys.addElement(line.substring(0, colon).trim());
                resVals.addElement(line.substring(colon + 1).trim());
            }
            if (code != 100) break; /* skip "100 Continue" */
        }
        String te = getHeaderField("transfer-encoding");
        String cl = getHeaderField("content-length");
        if (HEAD.equals(method) || code == 204 || code == 304) bodyIn = new LimitedIn(in, 0);
        else if (te != null && te.toLowerCase().indexOf("chunked") >= 0) bodyIn = new ChunkedIn(in);
        else if (cl != null) {
            long n;
            try {
                n = Long.parseLong(cl.trim());
            } catch (NumberFormatException e) {
                n = -1;
            }
            bodyIn = n >= 0 ? (InputStream) new LimitedIn(in, n) : in;
        } else bodyIn = in;
    }

    public int getResponseCode() throws IOException {
        connect();
        return code;
    }

    public String getResponseMessage() throws IOException {
        connect();
        return message;
    }

    public String getType() {
        try {
            return getHeaderField("content-type");
        } catch (IOException e) {
            return null;
        }
    }

    public String getEncoding() {
        try {
            return getHeaderField("content-encoding");
        } catch (IOException e) {
            return null;
        }
    }

    public long getLength() {
        try {
            String s = getHeaderField("content-length");
            return s == null ? -1 : Long.parseLong(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    public long getExpiration() throws IOException { return getHeaderFieldDate("expires", 0); }

    public long getDate() throws IOException { return getHeaderFieldDate("date", 0); }

    public long getLastModified() throws IOException { return getHeaderFieldDate("last-modified", 0); }

    public String getHeaderField(String name) throws IOException {
        connect();
        if (name == null) return null;
        for (int i = 0; i < resKeys.size(); i++)
            if (((String) resKeys.elementAt(i)).equalsIgnoreCase(name)) return (String) resVals.elementAt(i);
        return null;
    }

    public int getHeaderFieldInt(String name, int def) throws IOException {
        String s = getHeaderField(name);
        try {
            return s == null ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public long getHeaderFieldDate(String name, long def) throws IOException {
        String s = getHeaderField(name);
        if (s == null) return def;
        long d = parseDate(s);
        return d < 0 ? def : d;
    }

    /** RFC 1123 dates: "Sun, 06 Nov 1994 08:49:37 GMT". */
    static long parseDate(String s) {
        try {
            String months = "JanFebMarAprMayJunJulAugSepOctNovDec";
            int comma = s.indexOf(',');
            String r = (comma >= 0 ? s.substring(comma + 1) : s).trim();
            int day = Integer.parseInt(r.substring(0, 2).trim());
            int sp = r.indexOf(' ');
            String mon = r.substring(sp + 1, sp + 4);
            int month = months.indexOf(mon) / 3;
            int year = Integer.parseInt(r.substring(sp + 5, sp + 9));
            int hh = Integer.parseInt(r.substring(sp + 10, sp + 12));
            int mm = Integer.parseInt(r.substring(sp + 13, sp + 15));
            int ss = Integer.parseInt(r.substring(sp + 16, sp + 18));
            if (month < 0) return -1;
            java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT"));
            c.set(java.util.Calendar.YEAR, year);
            c.set(java.util.Calendar.MONTH, month);
            c.set(java.util.Calendar.DAY_OF_MONTH, day);
            c.set(java.util.Calendar.HOUR_OF_DAY, hh);
            c.set(java.util.Calendar.MINUTE, mm);
            c.set(java.util.Calendar.SECOND, ss);
            c.set(java.util.Calendar.MILLISECOND, 0);
            return c.getTime().getTime();
        } catch (Exception e) {
            return -1;
        }
    }

    public String getHeaderField(int n) throws IOException {
        connect();
        return n >= 0 && n < resVals.size() ? (String) resVals.elementAt(n) : null;
    }

    public String getHeaderFieldKey(int n) throws IOException {
        connect();
        return n >= 0 && n < resKeys.size() ? (String) resKeys.elementAt(n) : null;
    }

    public SecurityInfo getSecurityInfo() throws IOException { throw new IOException("Not a secure connection"); }

    public InputStream openInputStream() throws IOException {
        if (state == CLOSED) throw new IOException("Connection closed");
        if (inOpened) throw new IOException("Input stream already opened");
        connect();
        inOpened = true;
        return bodyIn;
    }

    public DataInputStream openDataInputStream() throws IOException { return new DataInputStream(openInputStream()); }

    public OutputStream openOutputStream() throws IOException {
        checkSetup();
        if (outOpened) throw new IOException("Output stream already opened");
        outOpened = true;
        if (body == null) body = new ReqOut();
        return body;
    }

    public DataOutputStream openDataOutputStream() throws IOException { return new DataOutputStream(openOutputStream()); }

    public void close() {
        state = CLOSED;
        if (sock != null) sock.close();
    }

    /* ---- body streams ---- */
    static final class BufferedIn extends InputStream {
        private final InputStream in;
        private final byte[] buf = new byte[4096];
        private int pos, len;

        BufferedIn(InputStream in) { this.in = in; }

        private boolean fill() throws IOException {
            if (pos < len) return true;
            int n = in.read(buf, 0, buf.length);
            if (n <= 0) return false;
            pos = 0;
            len = n;
            return true;
        }

        public int read() throws IOException { return fill() ? buf[pos++] & 0xFF : -1; }

        public int read(byte[] b, int off, int n) throws IOException {
            if (n == 0) return 0;
            if (!fill()) return -1;
            int k = Math.min(n, len - pos);
            System.arraycopy(buf, pos, b, off, k);
            pos += k;
            return k;
        }

        public int available() throws IOException { return (len - pos) + in.available(); }
    }

    static final class LimitedIn extends InputStream {
        private final InputStream in;
        private long left;

        LimitedIn(InputStream in, long n) {
            this.in = in;
            left = n;
        }

        public int read() throws IOException {
            if (left <= 0) return -1;
            int c = in.read();
            if (c >= 0) left--;
            return c;
        }

        public int read(byte[] b, int off, int n) throws IOException {
            if (left <= 0) return -1;
            if (n > left) n = (int) left;
            int k = in.read(b, off, n);
            if (k > 0) left -= k;
            return k;
        }

        public int available() throws IOException { return (int) Math.min(left, in.available()); }
    }

    final class ChunkedIn extends InputStream {
        private final InputStream in;
        private long left;
        private boolean done;

        ChunkedIn(InputStream in) { this.in = in; }

        private boolean next() throws IOException {
            if (done) return false;
            if (left > 0) return true;
            String line = readLine(in);
            if (line != null && line.length() == 0) line = readLine(in); /* CRLF after previous chunk */
            if (line == null) {
                done = true;
                return false;
            }
            int semi = line.indexOf(';');
            if (semi >= 0) line = line.substring(0, semi);
            left = Long.parseLong(line.trim(), 16);
            if (left == 0) {
                String l;
                while ((l = readLine(in)) != null && l.length() > 0) {}
                done = true;
                return false;
            }
            return true;
        }

        public int read() throws IOException {
            if (!next()) return -1;
            int c = in.read();
            if (c >= 0) left--;
            return c;
        }

        public int read(byte[] b, int off, int n) throws IOException {
            if (n == 0) return 0;
            if (!next()) return -1;
            if (n > left) n = (int) left;
            int k = in.read(b, off, n);
            if (k > 0) left -= k;
            return k;
        }

        public int available() throws IOException { return done ? 0 : (int) Math.min(left, in.available()); }
    }
}
