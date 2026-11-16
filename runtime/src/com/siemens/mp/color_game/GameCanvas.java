package com.siemens.mp.color_game;

/**
 * Siemens copy of the MIDP-2 GameCanvas. It is the platform GameCanvas (off-screen buffer,
 * key state latching, flushGraphics) under the Siemens name.
 */
public abstract class GameCanvas extends javax.microedition.lcdui.game.GameCanvas {
    public static final int UP_PRESSED = 1 << UP;
    public static final int DOWN_PRESSED = 1 << DOWN;
    public static final int LEFT_PRESSED = 1 << LEFT;
    public static final int RIGHT_PRESSED = 1 << RIGHT;
    public static final int FIRE_PRESSED = 1 << FIRE;
    public static final int GAME_A_PRESSED = 1 << GAME_A;
    public static final int GAME_B_PRESSED = 1 << GAME_B;
    public static final int GAME_C_PRESSED = 1 << GAME_C;
    public static final int GAME_D_PRESSED = 1 << GAME_D;

    protected GameCanvas(boolean suppressKeyEvents) { super(suppressKeyEvents); }
}
