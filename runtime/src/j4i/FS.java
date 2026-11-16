package j4i;

/** Path helpers for the sandboxed virtual file system exposed through Sys.fs*. */
public final class FS {
    private FS() {}

    /** Default root for JSR-75 ("file:///root1/") and vendor file APIs. */
    public static final String ROOT = "/root1/";

    /**
     * Normalizes a path: forces a leading "/", collapses "//", resolves "." and "..",
     * and never escapes the top level. Returns e.g. "/root1/a/b".
     */
    public static String normalize(String p) {
        if (p == null) return "/";
        StringBuffer out = new StringBuffer();
        int depthStart[] = new int[64];
        int depth = 0;
        int i = 0, n = p.length();
        boolean trailing = n > 0 && p.charAt(n - 1) == '/';
        while (i < n) {
            while (i < n && (p.charAt(i) == '/' || p.charAt(i) == '\\')) i++;
            int s = i;
            while (i < n && p.charAt(i) != '/' && p.charAt(i) != '\\') i++;
            if (s == i) break;
            String seg = p.substring(s, i);
            if (seg.equals(".")) continue;
            if (seg.equals("..")) {
                if (depth > 0) out.setLength(depthStart[--depth]);
                continue;
            }
            if (depth < depthStart.length) depthStart[depth++] = out.length();
            out.append('/').append(seg);
        }
        if (out.length() == 0) return "/";
        if (trailing) out.append('/');
        return out.toString();
    }

    /** Maps a vendor-relative file name ("save.dat") into the default root. */
    public static String vendorPath(String name) {
        if (name == null) name = "";
        if (name.startsWith("/")) return normalize(name);
        return normalize(ROOT + name);
    }
}
