package com.nokia.mid.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.J4IBridge;

public class DirectUtils {
    private DirectUtils() {}

    public static DirectGraphics getDirectGraphics(Graphics g) {
        if (g == null) throw new NullPointerException();
        return new DirectGraphicsImpl(g);
    }

    public static Image createImage(byte[] imageData, int imageOffset, int imageLength) {
        Image src = Image.createImage(imageData, imageOffset, imageLength);
        int w = src.getWidth(), h = src.getHeight();
        int[] p = new int[w * h];
        System.arraycopy(J4IBridge.pixels(src), 0, p, 0, p.length);
        return J4IBridge.wrap(p, w, h, true);
    }

    public static Image createImage(int width, int height, int argb) { return J4IBridge.createARGB(width, height, argb); }
}
