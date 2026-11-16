package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public abstract class GameCanvas extends Canvas {
    public static final int UP_PRESSED = 1 << UP;
    public static final int DOWN_PRESSED = 1 << DOWN;
    public static final int LEFT_PRESSED = 1 << LEFT;
    public static final int RIGHT_PRESSED = 1 << RIGHT;
    public static final int FIRE_PRESSED = 1 << FIRE;
    public static final int GAME_A_PRESSED = 1 << GAME_A;
    public static final int GAME_B_PRESSED = 1 << GAME_B;
    public static final int GAME_C_PRESSED = 1 << GAME_C;
    public static final int GAME_D_PRESSED = 1 << GAME_D;

    private final Image buffer;
    private final boolean suppress;
    private int keyStates, latched;

    protected GameCanvas(boolean suppressKeyEvents) {
        suppress = suppressKeyEvents;
        buffer = Image.createImage(j4i.Sys.screenWidth(), j4i.Sys.screenHeight());
    }

    protected Graphics getGraphics() { return buffer.getGraphics(); }

    public int getKeyStates() {
        int s = keyStates | latched;
        latched = 0;
        return s;
    }

    public void paint(Graphics g) { g.drawImage(buffer, 0, 0, Graphics.TOP | Graphics.LEFT); }

    public void flushGraphics(int x, int y, int width, int height) {
        javax.microedition.lcdui.J4IBridge.flush(this, buffer, x, y, width, height);
    }

    public void flushGraphics() { flushGraphics(0, 0, buffer.getWidth(), buffer.getHeight()); }

    /** Called by the event dispatcher before keyPressed/keyReleased. Returns true to swallow the event. */
    public boolean j4iGameKey(boolean pressed, int code) {
        int ga = getGameAction(code);
        int bit = ga != 0 ? 1 << ga : 0;
        if (pressed) {
            keyStates |= bit;
            latched |= bit;
        } else keyStates &= ~bit;
        return suppress && bit != 0;
    }
}
