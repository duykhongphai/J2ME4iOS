package javax.microedition.lcdui;

/** Internal access for javax.microedition.lcdui.game and vendor APIs. Not part of MIDP. */
public final class J4IBridge {
    private J4IBridge() {}

    public static void flush(Canvas c, Image buffer, int x, int y, int w, int h) {
        Display d = Display.instance;
        if (d != null) d.flushCanvasBuffer(c, buffer, x, y, w, h);
    }

    public static int[] pixels(Image img) { return img.pix; }

    public static Image createARGB(int w, int h, int argb) { return Image.createARGB(w, h, argb); }

    public static Image wrap(int[] pix, int w, int h, boolean mutable) { return new Image(pix, w, h, mutable); }

    public static void setARGBColor(Graphics g, int argb) { g.setARGB(argb); }

    public static int getARGBColor(Graphics g) { return g.argb; }

    public static void polygon(Graphics g, int[] xs, int xo, int[] ys, int yo, int n, int argb, boolean fill) {
        g.polygon(xs, xo, ys, yo, n, argb, fill);
    }

    public static void setFullScreen(Canvas c, boolean full) { c.fullScreen = full; }

    public static Graphics imageGraphics(Image img) { return new Graphics(img.pix, img.w, img.h, img); }

    public static int[] targetPixels(Graphics g) { return g.pix; }

    public static int targetWidth(Graphics g) { return g.pw; }

    public static int targetHeight(Graphics g) { return g.ph; }

    /** Absolute clip rectangle {x0, y0, x1, y1} (exclusive) and translation {tx, ty}. */
    public static void clipAndTranslate(Graphics g, int[] out6) {
        out6[0] = g.cx0;
        out6[1] = g.cy0;
        out6[2] = g.cx1;
        out6[3] = g.cy1;
        out6[4] = g.tx;
        out6[5] = g.ty;
    }

    public static boolean isMutable(Image img) { return img.mutable; }
}
