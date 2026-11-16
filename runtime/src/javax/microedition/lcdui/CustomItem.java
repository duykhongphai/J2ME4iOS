package javax.microedition.lcdui;

public abstract class CustomItem extends Item {
    protected static final int TRAVERSE_HORIZONTAL = 1;
    protected static final int TRAVERSE_VERTICAL = 2;
    protected static final int KEY_PRESS = 4;
    protected static final int KEY_RELEASE = 8;
    protected static final int KEY_REPEAT = 0x10;
    protected static final int POINTER_PRESS = 0x20;
    protected static final int POINTER_RELEASE = 0x40;
    protected static final int POINTER_DRAG = 0x80;
    protected static final int NONE = 0;

    protected CustomItem(String label) { super(label); }

    public int getGameAction(int keyCode) { return Screen.gameAction(keyCode); }

    protected final int getInteractionModes() {
        return TRAVERSE_HORIZONTAL | TRAVERSE_VERTICAL | KEY_PRESS | KEY_RELEASE | KEY_REPEAT | POINTER_PRESS | POINTER_RELEASE | POINTER_DRAG;
    }

    protected abstract int getMinContentWidth();

    protected abstract int getMinContentHeight();

    protected abstract int getPrefContentWidth(int height);

    protected abstract int getPrefContentHeight(int width);

    protected abstract void paint(Graphics g, int w, int h);

    protected final void invalidate() { repaintOwner(); }

    protected final void repaint() { repaintOwner(); }

    protected final void repaint(int x, int y, int w, int h) { repaintOwner(); }

    protected boolean traverse(int dir, int viewportWidth, int viewportHeight, int[] visRect_inout) { return false; }

    protected void traverseOut() {}

    protected void keyPressed(int keyCode) {}

    protected void keyReleased(int keyCode) {}

    protected void keyRepeated(int keyCode) {}

    protected void pointerPressed(int x, int y) {}

    protected void pointerReleased(int x, int y) {}

    protected void pointerDragged(int x, int y) {}

    protected void showNotify() {}

    protected void hideNotify() {}

    protected void sizeChanged(int w, int h) {}

    private int ch(int w) {
        int h = getPrefContentHeight(w);
        return h > 0 ? h : getMinContentHeight();
    }

    int contentHeight(int w) { return ch(w); }

    void paintContent(Graphics g, int x, int y, int w, boolean focused) {
        int cw = Math.min(w, Math.max(getMinContentWidth(), getPrefContentWidth(-1)));
        int h = ch(w);
        int ox = g.tx, oy = g.ty, c0 = g.cx0, c1 = g.cy0, c2 = g.cx1, c3 = g.cy1;
        g.translate(x, y);
        g.clipRect(0, 0, cw, h);
        try {
            paint(g, cw, h);
        } finally {
            g.tx = ox;
            g.ty = oy;
            g.cx0 = c0;
            g.cy0 = c1;
            g.cx1 = c2;
            g.cy1 = c3;
        }
    }

    boolean j4iFocusable() { return true; }

    boolean j4iMoveInside(int dir) {
        int[] r = new int[4];
        return traverse(dir < 0 ? Canvas.UP : Canvas.DOWN, Display.W, Display.H, r);
    }

    boolean j4iKey(int code) {
        keyPressed(code);
        return true;
    }

    void j4iTap(int x, int y, int w) {
        pointerPressed(x, y - labelHeight());
        pointerReleased(x, y - labelHeight());
    }

    void j4iShow() { showNotify(); }

    void j4iHide() { hideNotify(); }
}
