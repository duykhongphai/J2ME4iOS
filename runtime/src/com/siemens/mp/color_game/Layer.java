package com.siemens.mp.color_game;

import javax.microedition.lcdui.Graphics;

/** Siemens copy of the MIDP-2 game Layer for MIDP-1 phones. */
public abstract class Layer {
    int x, y, width, height;
    boolean visible = true;

    Layer(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void move(int dx, int dy) {
        x += dx;
        y += dy;
    }

    public final int getX() { return x; }

    public final int getY() { return y; }

    public final int getWidth() { return width; }

    public final int getHeight() { return height; }

    public void setVisible(boolean visible) { this.visible = visible; }

    public final boolean isVisible() { return visible; }

    public abstract void paint(Graphics g);
}
