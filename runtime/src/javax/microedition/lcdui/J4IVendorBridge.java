package javax.microedition.lcdui;

/** Internal access for vendor APIs (Siemens, Samsung, Motorola, LG). Not part of MIDP. */
public final class J4IVendorBridge {
    private J4IVendorBridge() {}

    /**
     * Draws an image straight onto the physical screen at (x, y) and presents it, like
     * Siemens ExtendedImage.blitToScreen. Soft keys / menus of the current Canvas stay on top.
     */
    public static void blitToScreen(Image img, int x, int y) {
        if (img == null) return;
        synchronized (Display.LOCK) {
            Display d = Display.instance;
            Graphics sg = Display.sg;
            if (d == null || sg == null) return;
            sg.reset();
            sg.drawImage(img, x, y, Graphics.TOP | Graphics.LEFT);
            sg.reset();
            if (d.current instanceof Canvas) ((Canvas) d.current).paintOverlay(sg);
            j4i.Sys.flush(Display.screen, Display.W, Display.H);
        }
    }

    /** The currently shown Displayable, or null. */
    public static Displayable current() {
        Display d = Display.instance;
        return d == null ? null : d.current;
    }
}
