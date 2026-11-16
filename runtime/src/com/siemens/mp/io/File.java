package com.siemens.mp.io;

import java.io.IOException;
import java.util.Hashtable;
import j4i.FS;
import j4i.Sys;

/**
 * Siemens file API on top of the virtual file system. Names may carry a drive prefix
 * ("a:/dir/x.dat", "0:\x.dat"); every name is mapped below "/root1/".
 * Integer results follow the Siemens convention: >= 0 success, -1 failure.
 */
public class File {
    public static final int INSIDE_STORAGE_PATH = 1;
    public static final int OUTSIDE_STORAGE_PATH = 0;
    public static final String STORAGE_DRIVE = "a:";

    private static final Hashtable OPEN = new Hashtable();
    private static int lastFd;

    /** An open file: whole content buffered, writes go straight to the VFS. */
    private static final class Handle {
        final String path;
        byte[] data;
        int len, pos;

        Handle(String path, byte[] d) {
            this.path = path;
            data = d == null ? new byte[0] : d;
            len = data.length;
        }

        void ensure(int n) {
            if (n <= data.length) return;
            byte[] nd = new byte[Math.max(n, data.length * 2 + 64)];
            System.arraycopy(data, 0, nd, 0, len);
            data = nd;
        }
    }

    public File() {}

    static String map(String fileName) {
        if (fileName == null) throw new NullPointerException();
        String n = fileName;
        int colon = n.indexOf(':');
        if (colon >= 0 && colon <= 2) n = n.substring(colon + 1);
        while (n.startsWith("/") || n.startsWith("\\")) n = n.substring(1);
        return FS.vendorPath(n.replace('\\', '/'));
    }

    private static Handle handle(int fd) { return (Handle) OPEN.get(new Integer(fd)); }

    public static String buildPath(String fileName) {
        return fileName;
    }

    public static int checkFileName(String fileName) {
        if (fileName == null) throw new NullPointerException();
        return fileName.indexOf(':') == -1 ? INSIDE_STORAGE_PATH : OUTSIDE_STORAGE_PATH;
    }

    /* ---------------- descriptor based I/O ---------------- */

    public int open(String fileName) throws IOException {
        String path = map(fileName);
        int t = Sys.fsType(path);
        if (t == 2) throw new IOException("Is a directory: " + fileName);
        byte[] d = null;
        if (t == 1) d = Sys.fsRead(path);
        else if (!Sys.fsWrite(path, new byte[0], 0, 0, false)) throw new IOException("Cannot create " + fileName);
        Handle h = new Handle(path, d);
        synchronized (OPEN) {
            int fd = ++lastFd;
            OPEN.put(new Integer(fd), h);
            return fd;
        }
    }

    public int close(int fileDescriptor) throws IOException {
        synchronized (OPEN) {
            return OPEN.remove(new Integer(fileDescriptor)) != null ? fileDescriptor : -1;
        }
    }

    public int length(int fileDescriptor) throws IOException {
        Handle h = handle(fileDescriptor);
        return h == null ? -1 : h.len;
    }

    public int read(int fileDescriptor, byte[] buf, int offset, int numBytes) throws IOException {
        Handle h = handle(fileDescriptor);
        if (h == null) return -1;
        if (buf == null) throw new NullPointerException();
        if (offset < 0 || numBytes < 0 || offset + numBytes > buf.length) throw new IndexOutOfBoundsException();
        if (h.pos >= h.len) return numBytes == 0 ? 0 : -1;
        int n = Math.min(numBytes, h.len - h.pos);
        System.arraycopy(h.data, h.pos, buf, offset, n);
        h.pos += n;
        return n;
    }

    public int write(int fileDescriptor, byte[] buf, int offset, int numBytes) throws IOException {
        Handle h = handle(fileDescriptor);
        if (h == null) return -1;
        if (buf == null) throw new NullPointerException();
        if (offset < 0 || numBytes < 0 || offset + numBytes > buf.length) throw new IndexOutOfBoundsException();
        boolean append = h.pos == h.len;
        h.ensure(h.pos + numBytes);
        System.arraycopy(buf, offset, h.data, h.pos, numBytes);
        h.pos += numBytes;
        if (h.pos > h.len) h.len = h.pos;
        boolean ok = append ? Sys.fsWrite(h.path, buf, offset, numBytes, true) : Sys.fsWrite(h.path, h.data, 0, h.len, false);
        if (!ok) throw new IOException("write failed");
        return numBytes;
    }

    public int seek(int fileDescriptor, int seekpos) throws IOException {
        Handle h = handle(fileDescriptor);
        if (h == null) return -1;
        if (seekpos < 0) throw new IOException("negative seek");
        if (seekpos > h.len) {
            h.ensure(seekpos);
            for (int i = h.len; i < seekpos; i++) h.data[i] = 0;
            h.len = seekpos;
            Sys.fsWrite(h.path, h.data, 0, h.len, false);
        }
        h.pos = seekpos;
        return h.pos;
    }

    public static void truncate(int fileDescriptor, int size) throws IOException {
        Handle h = handle(fileDescriptor);
        if (h == null) return;
        if (size < 0) throw new IOException("negative size");
        if (size < h.len) {
            h.len = size;
            if (h.pos > size) h.pos = size;
            if (!Sys.fsTruncate(h.path, size)) Sys.fsWrite(h.path, h.data, 0, h.len, false);
        }
    }

    /* ---------------- name based operations ---------------- */

    public static int exists(String fileName) throws IOException { return Sys.fsType(map(fileName)) != 0 ? 1 : -1; }

    public static boolean isDirectory(String fileName) throws IOException { return Sys.fsType(map(fileName)) == 2; }

    public static boolean getIsHidden(String fileName) throws IOException {
        String p = map(fileName);
        int s = p.lastIndexOf('/');
        return p.length() > s + 1 && p.charAt(s + 1) == '.';
    }

    public static long getLastModified(String fileName) throws IOException { return Sys.fsModified(map(fileName)); }

    public static int delete(String fileName) { return Sys.fsDelete(map(fileName)) ? 1 : -1; }

    public static int rename(String source, String dest) { return Sys.fsRename(map(source), map(dest)) ? 1 : -1; }

    public static int copy(String source, String dest) throws IOException {
        byte[] d = Sys.fsRead(map(source));
        if (d == null) throw new IOException("Cannot read " + source);
        if (!Sys.fsWrite(map(dest), d, 0, d.length, false)) throw new IOException("Cannot write " + dest);
        return 1;
    }

    public static int debugWrite(String fileName, String infoString) throws IOException {
        if (infoString == null) return 1;
        byte[] b = infoString.getBytes();
        if (!Sys.fsWrite(map(fileName), b, 0, b.length, true)) throw new IOException("Cannot write " + fileName);
        return 1;
    }

    public static String[] list(String pathName) throws IOException { return list(pathName, false); }

    public static String[] list(String pathName, boolean includeHidden) throws IOException {
        String[] all = Sys.fsList(map(pathName == null ? "" : pathName));
        if (all == null) return new String[0];
        int n = 0;
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            String s = all[i];
            if (s == null || s.length() == 0) continue;
            if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
            if (!includeHidden && s.startsWith(".")) continue;
            out[n++] = s;
        }
        if (n == out.length) return out;
        String[] r = new String[n];
        System.arraycopy(out, 0, r, 0, n);
        return r;
    }

    public static int spaceAvailable() throws IOException {
        long f = Sys.fsFree();
        return f > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, f);
    }
}
