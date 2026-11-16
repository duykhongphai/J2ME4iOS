package com.siemens.mp.ui;

import java.io.IOException;
import javax.microedition.lcdui.J4IBridge;

/**
 * Siemens image helpers (SL45i era API). Bitmaps are 1 bit per pixel, MSB = leftmost pixel,
 * bit set = black, rows padded to whole bytes. Transparent bitmaps use 2 bits per pixel:
 * 1x = black, 01 = white, 00 = transparent.
 */
public class Image extends com.siemens.mp.misc.NativeMem {
    public static final int COLOR_BMP_8BIT = 5;

    private javax.microedition.lcdui.Image img;

    public Image(int imageWidth, int imageHeight) {
        img = javax.microedition.lcdui.Image.createImage(imageWidth, imageHeight);
    }

    public Image(byte[] imageData, int imageWidth, int imageHeight) {
        img = mutableCopy(createImageFromBitmap(imageData, imageWidth, imageHeight));
    }

    public Image(byte[] imageData, int imageWidth, int imageHeight, boolean transparent) {
        img = mutableCopy(transparent ? createTransparentImageFromBitmap(imageData, imageWidth, imageHeight)
                                      : createImageFromBitmap(imageData, imageWidth, imageHeight));
    }

    public Image(javax.microedition.lcdui.Image image) { img = mutableCopy(image); }

    public int getWidth() { return img.getWidth(); }

    public int getHeight() { return img.getHeight(); }

    /** The underlying MIDP image of a Siemens image. */
    public static javax.microedition.lcdui.Image getNativeImage(Image image) { return image == null ? null : image.img; }

    static javax.microedition.lcdui.Image mutableCopy(javax.microedition.lcdui.Image src) {
        int w = src.getWidth(), h = src.getHeight();
        javax.microedition.lcdui.Image m = J4IBridge.createARGB(w, h, 0);
        System.arraycopy(J4IBridge.pixels(src), 0, J4IBridge.pixels(m), 0, w * h);
        return m;
    }

    /* ---------------- bitmap decoding ---------------- */

    public static javax.microedition.lcdui.Image createImageFromBitmap(byte[] imageData, int imageWidth, int imageHeight) {
        return createImageFromBitmap(imageData, null, imageWidth, imageHeight);
    }

    /** 1-bit image with an optional 1-bit mask (mask bit set = visible). */
    public static javax.microedition.lcdui.Image createImageFromBitmap(byte[] imageData, byte[] alpha, int imageWidth, int imageHeight) {
        return fromBitmap(imageData, 0, alpha, 0, imageWidth, imageHeight);
    }

    /** Shared decoder honoring data offsets (used by Siemens game.Sprite). Returns a mutable image. */
    public static javax.microedition.lcdui.Image fromBitmap(byte[] data, int off, byte[] mask, int moff, int w, int h) {
        if (data == null) return null;
        if (w <= 0 || h <= 0) throw new IllegalArgumentException();
        int stride = (w + 7) >> 3;
        javax.microedition.lcdui.Image out = J4IBridge.createARGB(w, h, 0);
        int[] p = J4IBridge.pixels(out);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int bi = off + y * stride + (x >> 3);
                int bit = 0x80 >> (x & 7);
                boolean black = bi < data.length && (data[bi] & bit) != 0;
                boolean vis = true;
                if (mask != null) {
                    int mi = moff + y * stride + (x >> 3);
                    vis = mi < mask.length && (mask[mi] & bit) != 0;
                }
                p[y * w + x] = vis ? (black ? 0xFF000000 : 0xFFFFFFFF) : 0x00FFFFFF;
            }
        }
        return out;
    }

    public static javax.microedition.lcdui.Image createTransparentImageFromBitmap(byte[] imageData, int imageWidth, int imageHeight) {
        if (imageData == null) return null;
        int w = imageWidth, h = imageHeight;
        if (w <= 0 || h <= 0) throw new IllegalArgumentException();
        int stride = (w + 3) >> 2;
        javax.microedition.lcdui.Image out = J4IBridge.createARGB(w, h, 0);
        int[] p = J4IBridge.pixels(out);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int bi = y * stride + (x >> 2);
                int v = bi < imageData.length ? (imageData[bi] >> (6 - 2 * (x & 3))) & 3 : 0;
                p[y * w + x] = (v & 2) != 0 ? 0xFF000000 : (v == 1 ? 0xFFFFFFFF : 0x00FFFFFF);
            }
        }
        return out;
    }

    /** Pixels where the mask is light (white) become transparent. */
    public static javax.microedition.lcdui.Image createTransparentImageFromMask(javax.microedition.lcdui.Image image,
                                                                             javax.microedition.lcdui.Image mask) {
        int w = image.getWidth(), h = image.getHeight();
        javax.microedition.lcdui.Image out = J4IBridge.createARGB(w, h, 0);
        int[] p = J4IBridge.pixels(out), src = J4IBridge.pixels(image);
        System.arraycopy(src, 0, p, 0, w * h);
        if (mask != null) {
            int[] m = J4IBridge.pixels(mask);
            int mw = mask.getWidth(), mh = mask.getHeight();
            for (int y = 0; y < h && y < mh; y++)
                for (int x = 0; x < w && x < mw; x++)
                    if (!maskSet(m[y * mw + x])) p[y * w + x] = 0;
        }
        return out;
    }

    /** A mask pixel counts as "set" (visible) unless it is opaque and light. */
    public static boolean maskSet(int argb) {
        if ((argb >>> 24) < 0x80) return true;
        return luma(argb) < 128;
    }

    public static int luma(int argb) {
        return (((argb >> 16) & 0xFF) * 77 + ((argb >> 8) & 0xFF) * 150 + (argb & 0xFF) * 29) >> 8;
    }

    /* ---------------- loading and transforms ---------------- */

    public static javax.microedition.lcdui.Image createImageWithScaling(String name) throws IOException {
        return javax.microedition.lcdui.Image.createImage(name);
    }

    public static javax.microedition.lcdui.Image createImageWithoutScaling(String name) throws IOException {
        return javax.microedition.lcdui.Image.createImage(name);
    }

    public static javax.microedition.lcdui.Image mirrorImageHorizontally(javax.microedition.lcdui.Image image) {
        return javax.microedition.lcdui.Image.createImage(image, 0, 0, image.getWidth(), image.getHeight(), 2 /* TRANS_MIRROR */);
    }

    public static javax.microedition.lcdui.Image mirrorImageVertically(javax.microedition.lcdui.Image image) {
        return javax.microedition.lcdui.Image.createImage(image, 0, 0, image.getWidth(), image.getHeight(), 1 /* TRANS_MIRROR_ROT180 */);
    }

    public static int getPixelColor(javax.microedition.lcdui.Image image, int x, int y) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) throw new IllegalArgumentException();
        return J4IBridge.pixels(image)[y * image.getWidth() + x];
    }

    public static void setPixelColor(javax.microedition.lcdui.Image image, int x, int y, int color) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) throw new IllegalArgumentException();
        if ((color >>> 24) == 0) color |= 0xFF000000;
        J4IBridge.pixels(image)[y * image.getWidth() + x] = color;
    }
}
