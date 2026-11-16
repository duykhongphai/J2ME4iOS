package j4i;

/** Native rasterizer. Clip rectangles are absolute: [x0,x1) x [y0,y1). */
public final class Gfx {
    private Gfx() {}

    public static native void fillRect(int[] d, int dw, int dh, int x0, int y0, int x1, int y1, int argb, int x, int y, int w, int h);

    public static native void drawLine(int[] d, int dw, int dh, int x0, int y0, int x1, int y1, int argb, boolean dotted,
                                       int xa, int ya, int xb, int yb);

    public static native void arc(int[] d, int dw, int dh, int x0, int y0, int x1, int y1, int argb, boolean fill, boolean dotted,
                                  int x, int y, int w, int h, int start, int arc);

    public static native void roundRect(int[] d, int dw, int dh, int x0, int y0, int x1, int y1, int argb, boolean fill,
                                        boolean dotted, int x, int y, int w, int h, int aw, int ah);

    public static native void fillTriangle(int[] d, int dw, int dh, int x0, int y0, int x1, int y1, int argb,
                                           int xa, int ya, int xb, int yb, int xc, int yc);

    public static native void polygon(int[] d, int dw, int dh, int x0, int y0, int x1, int y1, int argb,
                                      int[] xs, int xoff, int[] ys, int yoff, int n, int tx, int ty, boolean fill);

    public static native void drawRegion(int[] d, int dw, int dh, int x0, int y0, int x1, int y1,
                                         int[] src, int sw, int sh, int sx, int sy, int w, int h, int transform, int dx, int dy);

    public static native void drawRGB(int[] d, int dw, int dh, int x0, int y0, int x1, int y1,
                                      int[] rgb, int off, int scan, int x, int y, int w, int h, boolean alpha);

    public static native void copyArea(int[] d, int dw, int dh, int x0, int y0, int x1, int y1,
                                       int sx, int sy, int w, int h, int dx, int dy);

    public static native void drawString(int[] d, int dw, int dh, int x0, int y0, int x1, int y1, int argb,
                                         String s, int off, int len, int x, int y, int size, int style);

    public static native int stringWidth(String s, int off, int len, int size, int style);

    public static native int charsWidth(char[] c, int off, int len, int size, int style);

    public static native void fontInfo(int size, int style, int[] out);

    /** Decode PNG (or host supported format). info = {w, h, hasAlpha}. */
    public static native int[] decode(byte[] data, int off, int len, int[] info);
}
