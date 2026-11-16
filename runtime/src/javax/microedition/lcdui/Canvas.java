package javax.microedition.lcdui;

public abstract class Canvas extends Displayable {
    public static final int UP = 1;
    public static final int DOWN = 6;
    public static final int LEFT = 2;
    public static final int RIGHT = 5;
    public static final int FIRE = 8;
    public static final int GAME_A = 9;
    public static final int GAME_B = 10;
    public static final int GAME_C = 11;
    public static final int GAME_D = 12;
    public static final int KEY_NUM0 = 48;
    public static final int KEY_NUM1 = 49;
    public static final int KEY_NUM2 = 50;
    public static final int KEY_NUM3 = 51;
    public static final int KEY_NUM4 = 52;
    public static final int KEY_NUM5 = 53;
    public static final int KEY_NUM6 = 54;
    public static final int KEY_NUM7 = 55;
    public static final int KEY_NUM8 = 56;
    public static final int KEY_NUM9 = 57;
    public static final int KEY_STAR = 42;
    public static final int KEY_POUND = 35;

    boolean fullScreen;

    protected Canvas() {}

    public boolean isDoubleBuffered() { return true; }

    public boolean hasPointerEvents() { return true; }

    public boolean hasPointerMotionEvents() { return true; }

    public boolean hasRepeatEvents() { return true; }

    public int getKeyCode(int gameAction) {
        switch (gameAction) {
        case UP: return -1;
        case DOWN: return -2;
        case LEFT: return -3;
        case RIGHT: return -4;
        case FIRE: return -5;
        case GAME_A: return KEY_NUM1;
        case GAME_B: return KEY_NUM3;
        case GAME_C: return KEY_NUM7;
        case GAME_D: return KEY_NUM9;
        }
        throw new IllegalArgumentException();
    }

    public String getKeyName(int keyCode) {
        switch (keyCode) {
        case -1: return "Up";
        case -2: return "Down";
        case -3: return "Left";
        case -4: return "Right";
        case -5: return "Select";
        case -6: return "Soft1";
        case -7: return "Soft2";
        case -8: return "Clear";
        case KEY_STAR: return "*";
        case KEY_POUND: return "#";
        }
        if (keyCode >= KEY_NUM0 && keyCode <= KEY_NUM9) return String.valueOf((char) keyCode);
        return "Key " + keyCode;
    }

    public int getGameAction(int keyCode) {
        switch (keyCode) {
        case -1: case KEY_NUM2: return UP;
        case -2: case KEY_NUM8: return DOWN;
        case -3: case KEY_NUM4: return LEFT;
        case -4: case KEY_NUM6: return RIGHT;
        case -5: case KEY_NUM5: return FIRE;
        case KEY_NUM1: return GAME_A;
        case KEY_NUM3: return GAME_B;
        case KEY_NUM7: return GAME_C;
        case KEY_NUM9: return GAME_D;
        }
        return 0;
    }

    public void setFullScreenMode(boolean mode) {
        if (fullScreen == mode) return;
        fullScreen = mode;
        repaintSelf();
    }

    protected void keyPressed(int keyCode) {}

    protected void keyRepeated(int keyCode) {}

    protected void keyReleased(int keyCode) {}

    protected void pointerPressed(int x, int y) {}

    protected void pointerReleased(int x, int y) {}

    protected void pointerDragged(int x, int y) {}

    public final void repaint(int x, int y, int width, int height) { repaintSelf(); }

    public final void repaint() { repaintSelf(); }

    public final void serviceRepaints() {
        Display d = Display.instance;
        if (d != null && d.current == this) d.serviceRepaints();
    }

    protected void showNotify() {}

    protected void hideNotify() {}

    protected abstract void paint(Graphics g);

    protected void sizeChanged(int w, int h) {}

    /* ---------- internals ---------- */
    boolean hasSoftBar() { return !fullScreen; }

    void paintOverlay(Graphics g) {
        if (!fullScreen && !cmds.isEmpty()) Display.paintSoftBar(g, this, true);
    }

    void j4iPaint(Graphics g) {
        g.setClip(0, 0, Display.W, Display.H);
        paint(g);
        g.reset();
        paintOverlay(g);
    }

    void j4iShow() { showNotify(); }

    void j4iHide() { hideNotify(); }

    void j4iKey(int type, int code) {
        if (this instanceof javax.microedition.lcdui.game.GameCanvas) {
            javax.microedition.lcdui.game.GameCanvas gc = (javax.microedition.lcdui.game.GameCanvas) this;
            boolean swallow;
            if (type == Display.EV_KEY_REPEATED) swallow = gc.j4iGameKey(true, code) && getGameAction(code) != 0;
            else swallow = gc.j4iGameKey(type == Display.EV_KEY_PRESSED, code);
            if (swallow) return;
        }
        switch (type) {
        case Display.EV_KEY_PRESSED: keyPressed(code); break;
        case Display.EV_KEY_RELEASED: keyReleased(code); break;
        case Display.EV_KEY_REPEATED: keyRepeated(code); break;
        }
    }

    void j4iPointer(int type, int x, int y) {
        switch (type) {
        case Display.EV_POINTER_PRESSED: pointerPressed(x, y); break;
        case Display.EV_POINTER_RELEASED: pointerReleased(x, y); break;
        case Display.EV_POINTER_DRAGGED: pointerDragged(x, y); break;
        }
    }
}
