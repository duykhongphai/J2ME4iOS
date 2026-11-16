package com.siemens.mp.game;

import javax.microedition.lcdui.Graphics;

/** Base of Siemens Sprite and TiledBackground, drawn by GraphicObjectManager. */
public class GraphicObject extends com.siemens.mp.misc.NativeMem {
    private boolean visible = true;

    protected GraphicObject() {}

    public boolean getVisible() { return visible; }

    public void setVisible(boolean visible) { this.visible = visible; }

    /** Overridden by Sprite and TiledBackground. */
    void paint(Graphics g) {}
}
