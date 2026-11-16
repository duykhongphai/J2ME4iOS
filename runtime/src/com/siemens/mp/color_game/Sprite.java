package com.siemens.mp.color_game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * Siemens copy of the MIDP-2 Sprite. Delegates to javax.microedition.lcdui.game.Sprite;
 * position and visibility live in this Layer and are pushed to the delegate before use.
 */
public class Sprite extends Layer {
    public static final int TRANS_NONE = 0;
    public static final int TRANS_ROT90 = 5;
    public static final int TRANS_ROT180 = 3;
    public static final int TRANS_ROT270 = 6;
    public static final int TRANS_MIRROR = 2;
    public static final int TRANS_MIRROR_ROT90 = 7;
    public static final int TRANS_MIRROR_ROT180 = 1;
    public static final int TRANS_MIRROR_ROT270 = 4;

    final javax.microedition.lcdui.game.Sprite d;

    public Sprite(Image image) {
        super(image.getWidth(), image.getHeight());
        d = new javax.microedition.lcdui.game.Sprite(image);
        pull();
    }

    public Sprite(Image image, int frameWidth, int frameHeight) {
        super(frameWidth, frameHeight);
        d = new javax.microedition.lcdui.game.Sprite(image, frameWidth, frameHeight);
        pull();
    }

    public Sprite(Sprite s) {
        super(s.width, s.height);
        s.push();
        d = new javax.microedition.lcdui.game.Sprite(s.d);
        pull();
    }

    /** layer state -> delegate */
    final void push() {
        d.setPosition(x, y);
        d.setVisible(visible);
    }

    /** delegate -> layer state */
    final void pull() {
        x = d.getX();
        y = d.getY();
        width = d.getWidth();
        height = d.getHeight();
        visible = d.isVisible();
    }

    public void defineReferencePixel(int x, int y) { d.defineReferencePixel(x, y); }

    public void setRefPixelPosition(int x, int y) {
        push();
        d.setRefPixelPosition(x, y);
        pull();
    }

    public int getRefPixelX() {
        push();
        return d.getRefPixelX();
    }

    public int getRefPixelY() {
        push();
        return d.getRefPixelY();
    }

    public void setFrame(int sequenceIndex) { d.setFrame(sequenceIndex); }

    public final int getFrame() { return d.getFrame(); }

    public int getRawFrameCount() { return d.getRawFrameCount(); }

    public int getFrameSequenceLength() { return d.getFrameSequenceLength(); }

    public void nextFrame() { d.nextFrame(); }

    public void prevFrame() { d.prevFrame(); }

    public void setFrameSequence(int[] sequence) { d.setFrameSequence(sequence); }

    public void setImage(Image img, int frameWidth, int frameHeight) {
        push();
        d.setImage(img, frameWidth, frameHeight);
        pull();
    }

    public void defineCollisionRectangle(int x, int y, int width, int height) { d.defineCollisionRectangle(x, y, width, height); }

    /** Siemens name for defineCollisionRectangle. */
    public void setCollisionRectangle(int x, int y, int width, int height) { d.defineCollisionRectangle(x, y, width, height); }

    public void setTransform(int transform) {
        push();
        d.setTransform(transform);
        pull();
    }

    public final void paint(Graphics g) {
        if (!visible) return;
        push();
        d.paint(g);
    }

    public final boolean collidesWith(Sprite s, boolean pixelLevel) {
        push();
        s.push();
        return d.collidesWith(s.d, pixelLevel);
    }

    public final boolean collidesWith(TiledLayer t, boolean pixelLevel) {
        push();
        t.push();
        return d.collidesWith(t.d, pixelLevel);
    }

    public final boolean collidesWith(Image image, int x, int y, boolean pixelLevel) {
        push();
        return d.collidesWith(image, x, y, pixelLevel);
    }
}
