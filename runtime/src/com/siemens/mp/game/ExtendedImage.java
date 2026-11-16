package com.siemens.mp.game;

import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.J4IBridge;
import javax.microedition.lcdui.J4IVendorBridge;

/**
 * Siemens 1-bit view of a mutable MIDP image. Pixel values: 0 = white, 1 = black;
 * setPixel with any other value makes the pixel transparent. Bitmaps are packed MSB-first,
 * 8 pixels per byte, rows padded to whole bytes.
 */
public class ExtendedImage extends com.siemens.mp.misc.NativeMem {
    private final Image image;

    public ExtendedImage(Image image) {
        if (image == null) throw new NullPointerException();
        this.image = image;
    }

    public Image getImage() { return image; }

    public void blitToScreen(int x, int y) { J4IVendorBridge.blitToScreen(image, x, y); }

    private static int argbOf(int color) {
        if (color == 0) return 0xFFFFFFFF;
        if (color == 1) return 0xFF000000;
        return 0x00FFFFFF;
    }

    public void clear(byte color) {
        int c = argbOf(color);
        int[] p = J4IBridge.pixels(image);
        for (int i = 0; i < p.length; i++) p[i] = c;
    }

    public int getPixel(int x, int y) {
        int w = image.getWidth();
        if (x < 0 || y < 0 || x >= w || y >= image.getHeight()) return 0;
        return isBlack(J4IBridge.pixels(image)[y * w + x]) ? 1 : 0;
    }

    public void setPixel(int x, int y, byte color) {
        int w = image.getWidth();
        if (x < 0 || y < 0 || x >= w || y >= image.getHeight()) return;
        J4IBridge.pixels(image)[y * w + x] = argbOf(color);
    }

    static boolean isBlack(int argb) {
        return (argb >>> 24) >= 0x80 && com.siemens.mp.ui.Image.luma(argb) < 128;
    }

    /** Packs a region into 1-bit rows (bit set = black). */
    public void getPixelBytes(byte[] pixels, int x, int y, int width, int height) {
        if (pixels == null) throw new NullPointerException();
        int stride = (width + 7) >> 3;
        int iw = image.getWidth(), ih = image.getHeight();
        int[] p = J4IBridge.pixels(image);
        for (int r = 0; r < height; r++) {
            for (int b = 0; b < stride; b++) {
                int idx = r * stride + b;
                if (idx >= pixels.length) return;
                int v = 0;
                for (int k = 0; k < 8; k++) {
                    int px = x + b * 8 + k, py = y + r;
                    if (b * 8 + k < width && px >= 0 && py >= 0 && px < iw && py < ih && isBlack(p[py * iw + px])) v |= 0x80 >> k;
                }
                pixels[idx] = (byte) v;
            }
        }
    }

    /** Writes 1-bit rows (bit set = black, clear = white) into the image. */
    public void setPixels(byte[] pixels, int x, int y, int width, int height) {
        if (pixels == null) throw new NullPointerException();
        int stride = (width + 7) >> 3;
        int iw = image.getWidth(), ih = image.getHeight();
        int[] p = J4IBridge.pixels(image);
        for (int r = 0; r < height; r++) {
            int py = y + r;
            if (py < 0 || py >= ih) continue;
            for (int c = 0; c < width; c++) {
                int px = x + c;
                if (px < 0 || px >= iw) continue;
                int idx = r * stride + (c >> 3);
                if (idx >= pixels.length) return;
                p[py * iw + px] = (pixels[idx] & (0x80 >> (c & 7))) != 0 ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
    }
}
