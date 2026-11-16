package com.siemens.mp.game;

import java.util.Vector;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.J4IBridge;

/** Ordered list of Siemens GraphicObjects painted into an image (index 0 is drawn first). */
public class GraphicObjectManager extends com.siemens.mp.misc.NativeMem {
    private final Vector objects = new Vector();

    public GraphicObjectManager() {}

    public void addObject(GraphicObject gobject) {
        if (gobject == null) throw new NullPointerException();
        objects.addElement(gobject);
    }

    public void insertObject(GraphicObject gobject, int position) {
        if (gobject == null) throw new NullPointerException();
        objects.insertElementAt(gobject, position);
    }

    public void deleteObject(GraphicObject gobject) { objects.removeElement(gobject); }

    public void deleteObject(int position) { objects.removeElementAt(position); }

    public GraphicObject getObjectAt(int index) { return (GraphicObject) objects.elementAt(index); }

    public int getObjectPosition(GraphicObject gobject) { return objects.indexOf(gobject); }

    /** Not in every Siemens SDK; harmless helper. */
    public int size() { return objects.size(); }

    /**
     * Tiles an 8x8 1-bit texture (8 bytes, one per row) into a width x height 1-bit bitmap
     * usable as TiledBackground / Sprite pixel data.
     */
    public static byte[] createTextureBits(int width, int height, byte[] texture) {
        if (texture == null) throw new NullPointerException();
        if (width <= 0 || height <= 0) throw new IllegalArgumentException();
        int stride = (width + 7) >> 3;
        byte[] out = new byte[stride * height];
        if (texture.length == 0) return out;
        for (int y = 0; y < height; y++) {
            byte row = texture[y % Math.min(8, texture.length)];
            for (int b = 0; b < stride; b++) out[y * stride + b] = row;
        }
        return out;
    }

    public void paint(ExtendedImage eimage, int x, int y) { paint(eimage.getImage(), x, y); }

    /** Paints all visible objects into the image, offset by (x, y). */
    public void paint(Image image, int x, int y) {
        Graphics g = J4IBridge.imageGraphics(image);
        g.translate(x, y);
        for (int i = 0; i < objects.size(); i++) {
            GraphicObject go = (GraphicObject) objects.elementAt(i);
            if (go.getVisible()) go.paint(g);
        }
    }
}
