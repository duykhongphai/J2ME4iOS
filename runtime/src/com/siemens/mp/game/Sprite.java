package com.siemens.mp.game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.J4IBridge;

/** Siemens sprite: frames stacked vertically in one image, with an optional 1-bit mask. */
public class Sprite extends GraphicObject {
    private final Image frames;
    private final int fw, fh, numFrames;
    private int x, y, frame;
    private int collx, colly, collw, collh;

    /** 1-bit pixel and mask data; width x height is one frame, frames follow each other. */
    public Sprite(byte[] pixels, int pixel_offset, int width, int height, byte[] mask, int mask_offset, int numFrames) {
        this(com.siemens.mp.ui.Image.fromBitmap(pixels, pixel_offset, mask, mask_offset, width, height * Math.max(1, numFrames)),
             (Image) null, numFrames);
    }

    public Sprite(ExtendedImage pixels, ExtendedImage mask, int numFrames) {
        this(pixels.getImage(), mask == null ? null : mask.getImage(), numFrames);
    }

    public Sprite(Image pixels, Image mask, int numFrames) {
        if (pixels == null) throw new NullPointerException();
        if (numFrames < 1) numFrames = 1;
        frames = mask != null ? com.siemens.mp.ui.Image.createTransparentImageFromMask(pixels, mask) : pixels;
        this.numFrames = numFrames;
        fw = pixels.getWidth();
        fh = Math.max(1, pixels.getHeight() / numFrames);
        collw = fw;
        collh = fh;
    }

    public int getFrame() { return frame; }

    public int getXPosition() { return x; }

    public int getYPosition() { return y; }

    public boolean isCollidingWith(Sprite other) {
        if (other == null) return false;
        int l = x + collx, t = y + colly, r = l + collw, b = t + collh;
        int ol = other.x + other.collx, ot = other.y + other.colly;
        int or = ol + other.collw, ob = ot + other.collh;
        return l < or && ol < r && t < ob && ot < b;
    }

    public boolean isCollidingWithPos(int xpos, int ypos) {
        int l = x + collx, t = y + colly;
        return xpos >= l && xpos < l + collw && ypos >= t && ypos < t + collh;
    }

    public void setCollisionRectangle(int x, int y, int width, int height) {
        collx = x;
        colly = y;
        collw = width;
        collh = height;
    }

    public void setFrame(int framenumber) {
        if (framenumber >= 0 && framenumber < numFrames) frame = framenumber;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    protected void paint(Graphics g) {
        if (frame * fh + fh > frames.getHeight()) return;
        g.drawRegion(frames, 0, frame * fh, fw, fh, 0, x, y, Graphics.TOP | Graphics.LEFT);
    }

    /** True if the pixel at sprite-local (px, py) of the current frame is opaque. */
    boolean opaqueAt(int px, int py) {
        if (px < 0 || py < 0 || px >= fw || py >= fh) return false;
        return (J4IBridge.pixels(frames)[(frame * fh + py) * frames.getWidth() + px] >>> 24) != 0;
    }
}
