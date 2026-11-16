package j4i.net;

import j4i.FS;
import j4i.Sys;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.ConnectionClosedException;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.IllegalModeException;

/** JSR-75 FileConnection over the VM's sandboxed file system. */
public final class FileConn implements FileConnection {
    private final int mode;
    private String path; /* "/root1/dir/name" (directories keep a trailing "/") */
    private boolean open = true;
    private final Vector hidden = new Vector();

    public FileConn(String url, int mode) throws IOException {
        this.mode = mode;
        path = parse(url);
        ensureRoot();
    }

    /** "file:///root1/a%20b.txt" -> "/root1/a b.txt". Drive letters lose their colon. */
    static String parse(String url) throws IOException {
        if (!url.startsWith("file://")) throw new IllegalArgumentException(url);
        String p = url.substring(7);
        if (!p.startsWith("/")) {
            int s = p.indexOf('/'); /* file://host/path */
            if (s < 0) throw new IllegalArgumentException(url);
            p = p.substring(s);
        }
        p = unescape(p);
        boolean dir = p.endsWith("/");
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c != ':') sb.append(c);
        }
        String n = FS.normalize(sb.toString());
        if (n.equals("/")) throw new IllegalArgumentException("No file system root: " + url);
        if (dir && !n.endsWith("/")) n += "/";
        return n;
    }

    static String unescape(String s) {
        if (s.indexOf('%') < 0) return s;
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                try {
                    bo.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                } catch (NumberFormatException e) {
                }
            }
            if (c < 0x80) bo.write(c);
            else {
                byte[] u = String.valueOf(c).getBytes();
                bo.write(u, 0, u.length);
            }
        }
        try {
            return new String(bo.toByteArray(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    static String escape(String s) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') sb.append("%20");
            else if (c == '%') sb.append("%25");
            else if (c == '#') sb.append("%23");
            else sb.append(c);
        }
        return sb.toString();
    }

    private void ensureRoot() {
        int s = path.indexOf('/', 1);
        String root = s < 0 ? path : path.substring(0, s);
        if (Sys.fsType(root) == 0) Sys.fsMkdir(root);
    }

    private String target() { /* without trailing slash */
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private void checkOpen() {
        if (!open) throw new ConnectionClosedException("Connection closed");
    }

    private void checkRead() {
        checkOpen();
        if (mode == Connector.WRITE) throw new IllegalModeException("Write-only connection");
    }

    private void checkWrite() {
        checkOpen();
        if (mode == Connector.READ) throw new IllegalModeException("Read-only connection");
    }

    public boolean isOpen() { return open; }

    public InputStream openInputStream() throws IOException {
        checkRead();
        byte[] d = Sys.fsRead(target());
        if (d == null) throw new IOException(Sys.fsType(target()) == 2 ? "Is a directory" : "File does not exist: " + getURL());
        return new ByteArrayInputStream(d);
    }

    public DataInputStream openDataInputStream() throws IOException { return new DataInputStream(openInputStream()); }

    public OutputStream openOutputStream() throws IOException { return openOutputStream(0); }

    public DataOutputStream openDataOutputStream() throws IOException { return new DataOutputStream(openOutputStream()); }

    public OutputStream openOutputStream(long byteOffset) throws IOException {
        checkWrite();
        if (byteOffset < 0) throw new IllegalArgumentException();
        if (Sys.fsType(target()) != 1) throw new IOException("File does not exist: " + getURL());
        return new FileOut(target(), byteOffset);
    }

    /** Buffers writes and splices them into the file at the start offset on flush/close. */
    static final class FileOut extends OutputStream {
        private final String file;
        private long offset;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private boolean closed;

        FileOut(String file, long offset) {
            this.file = file;
            this.offset = offset;
        }

        public void write(int b) throws IOException {
            if (closed) throw new IOException("Stream closed");
            buf.write(b);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            if (closed) throw new IOException("Stream closed");
            buf.write(b, off, len);
            if (buf.size() > 256 * 1024) flush();
        }

        public void flush() throws IOException {
            if (buf.size() == 0) return;
            byte[] add = buf.toByteArray();
            buf.reset();
            long size = Sys.fsSize(file);
            if (size < 0) size = 0;
            boolean ok;
            if (offset >= size) {
                if (offset > size) {
                    byte[] pad = new byte[(int) (offset - size)];
                    Sys.fsWrite(file, pad, 0, pad.length, true);
                }
                ok = Sys.fsWrite(file, add, 0, add.length, true);
            } else {
                byte[] old = Sys.fsRead(file);
                if (old == null) old = new byte[0];
                int end = (int) Math.max(old.length, offset + add.length);
                byte[] all = new byte[end];
                System.arraycopy(old, 0, all, 0, old.length);
                System.arraycopy(add, 0, all, (int) offset, add.length);
                ok = Sys.fsWrite(file, all, 0, all.length, false);
            }
            if (!ok) throw new IOException("Write failed");
            offset += add.length;
        }

        public void close() throws IOException {
            if (closed) return;
            flush();
            closed = true;
        }
    }

    public long totalSize() {
        checkOpen();
        return usedSize() + availableSize();
    }

    public long availableSize() {
        checkOpen();
        return Sys.fsFree();
    }

    public long usedSize() {
        checkOpen();
        int s = path.indexOf('/', 1);
        String root = s < 0 ? path : path.substring(0, s);
        return dirSize(root, true);
    }

    private static long dirSize(String dir, boolean recurse) {
        String[] names = Sys.fsList(dir);
        long total = 0;
        if (names == null) return 0;
        for (int i = 0; i < names.length; i++) {
            String n = names[i];
            if (n.endsWith("/")) {
                if (recurse) total += dirSize(dir + "/" + n.substring(0, n.length() - 1), true);
            } else {
                long sz = Sys.fsSize(dir + "/" + n);
                if (sz > 0) total += sz;
            }
        }
        return total;
    }

    public long directorySize(boolean includeSubDirs) throws IOException {
        checkRead();
        if (Sys.fsType(target()) != 2) throw new IOException("Not a directory");
        return dirSize(target(), includeSubDirs);
    }

    public long fileSize() throws IOException {
        checkRead();
        if (Sys.fsType(target()) == 2) throw new IOException("Is a directory");
        return Sys.fsSize(target());
    }

    public boolean canRead() {
        checkRead();
        return Sys.fsType(target()) != 0;
    }

    public boolean canWrite() {
        checkRead();
        return Sys.fsType(target()) != 0;
    }

    public boolean isHidden() {
        checkRead();
        return getName().startsWith(".");
    }

    public void setReadable(boolean readable) throws IOException {
        checkWrite();
        if (!exists()) throw new IOException("Does not exist");
    }

    public void setWritable(boolean writable) throws IOException {
        checkWrite();
        if (!exists()) throw new IOException("Does not exist");
    }

    public void setHidden(boolean h) throws IOException {
        checkWrite();
        if (!exists()) throw new IOException("Does not exist");
    }

    public Enumeration list() throws IOException { return list("*", false); }

    public Enumeration list(String filter, boolean includeHidden) throws IOException {
        checkRead();
        if (filter == null) throw new NullPointerException();
        if (Sys.fsType(target()) != 2) throw new IOException("Not a directory: " + getURL());
        String[] names = Sys.fsList(target());
        Vector v = new Vector();
        if (names != null)
            for (int i = 0; i < names.length; i++) {
                String n = names[i];
                if (!includeHidden && n.startsWith(".")) continue;
                String bare = n.endsWith("/") ? n.substring(0, n.length() - 1) : n;
                if (match(filter.toLowerCase(), 0, bare.toLowerCase(), 0)) v.addElement(n);
            }
        return v.elements();
    }

    private static boolean match(String p, int pi, String s, int si) {
        while (pi < p.length()) {
            char c = p.charAt(pi);
            if (c == '*') {
                for (int k = s.length(); k >= si; k--)
                    if (match(p, pi + 1, s, k)) return true;
                return false;
            }
            if (si >= s.length() || (c != '?' && c != s.charAt(si))) return false;
            pi++;
            si++;
        }
        return si == s.length();
    }

    public void create() throws IOException {
        checkWrite();
        if (path.endsWith("/")) throw new IOException("Cannot create a directory with create()");
        if (Sys.fsType(target()) != 0) throw new IOException("Already exists: " + getURL());
        if (!Sys.fsWrite(target(), null, 0, 0, false)) throw new IOException("Cannot create " + getURL());
    }

    public void mkdir() throws IOException {
        checkWrite();
        if (Sys.fsType(target()) != 0) throw new IOException("Already exists: " + getURL());
        if (!Sys.fsMkdir(target())) throw new IOException("Cannot create directory " + getURL());
        if (!path.endsWith("/")) path += "/";
    }

    public boolean exists() {
        checkOpen();
        return Sys.fsType(target()) != 0;
    }

    public boolean isDirectory() {
        checkOpen();
        return Sys.fsType(target()) == 2;
    }

    public void delete() throws IOException {
        checkWrite();
        if (!Sys.fsDelete(target())) throw new IOException("Cannot delete " + getURL());
    }

    public void rename(String newName) throws IOException {
        checkWrite();
        if (newName == null) throw new NullPointerException();
        if (newName.indexOf('/') >= 0 && newName.indexOf('/') != newName.length() - 1) throw new IllegalArgumentException(newName);
        String t = target();
        String parent = t.substring(0, t.lastIndexOf('/') + 1);
        String bare = newName.endsWith("/") ? newName.substring(0, newName.length() - 1) : newName;
        String dst = parent + bare;
        if (!Sys.fsRename(t, dst)) throw new IOException("Cannot rename to " + newName);
        path = dst + (path.endsWith("/") ? "/" : "");
    }

    public void truncate(long byteOffset) throws IOException {
        checkWrite();
        if (byteOffset < 0) throw new IllegalArgumentException();
        if (Sys.fsType(target()) != 1) throw new IOException("Not a file");
        if (!Sys.fsTruncate(target(), byteOffset)) throw new IOException("Truncate failed");
    }

    public void setFileConnection(String fileName) throws IOException {
        checkOpen();
        if (fileName == null) throw new NullPointerException();
        if (Sys.fsType(target()) != 2) throw new IOException("Current connection is not a directory");
        String base = path.endsWith("/") ? path : path + "/";
        String np;
        if (fileName.equals("..")) {
            String t = target();
            np = t.substring(0, t.lastIndexOf('/') + 1);
            if (np.equals("/")) throw new IOException("Cannot leave the file system root");
        } else {
            if (fileName.indexOf('/') >= 0 && fileName.indexOf('/') != fileName.length() - 1) throw new IllegalArgumentException(fileName);
            np = base + fileName;
            String bare = np.endsWith("/") ? np.substring(0, np.length() - 1) : np;
            int ty = Sys.fsType(bare);
            if (ty == 0) throw new IllegalArgumentException("Does not exist: " + fileName);
            if (ty == 2 && !np.endsWith("/")) np += "/";
        }
        path = np;
    }

    public String getName() {
        String t = target();
        String n = t.substring(t.lastIndexOf('/') + 1);
        return path.endsWith("/") && path.length() > 1 ? n + "/" : n;
    }

    public String getPath() {
        String t = target();
        return t.substring(0, t.lastIndexOf('/') + 1);
    }

    public String getURL() { return "file://" + escape(path); }

    public long lastModified() {
        checkOpen();
        return Sys.fsModified(target());
    }

    public void close() { open = false; }
}
