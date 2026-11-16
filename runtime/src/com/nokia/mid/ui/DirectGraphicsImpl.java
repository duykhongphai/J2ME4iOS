package com.nokia.mid.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.J4IBridge;

final class DirectGraphicsImpl implements DirectGraphics {
    private final Graphics g;

    DirectGraphicsImpl(Graphics g) { this.g = g; }

    /** Nokia manipulation -> MIDP Sprite transform. Flips are applied before the (counter clockwise) rotation. */
    static int transform(int manipulation) {
        boolean fh = (manipulation & FLIP_HORIZONTAL) != 0, fv = (manipulation & FLIP_VERTICAL) != 0;
        int ccw = (manipulation & ~(FLIP_HORIZONTAL | FLIP_VERTICAL)) % 360;
        boolean mirror = fh ^ fv;
        int cw = ((fv ? 180 : 0) + (360 - ccw)) % 360;
        if (!mirror) {
            switch (cw) {
            case 90: return 5;
            case 180: return 3;
            case 270: return 6;
            default: return 0;
            }
        }
        switch (cw) {
        case 90: return 7;
        case 180: return 1;
        case 270: return 4;
        default: return 2;
        }
    }

    public void setARGBColor(int argbColor) { J4IBridge.setARGBColor(g, argbColor); }

    public void drawImage(Image img, int x, int y, int anchor, int manipulation) {
        if (img == null) throw new NullPointerException();
        g.drawRegion(img, 0, 0, img.getWidth(), img.getHeight(), transform(manipulation), x, y, anchor == 0 ? Graphics.TOP | Graphics.LEFT : anchor);
    }

    public void drawTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor) {
        int[] xs = {x1, x2, x3}, ys = {y1, y2, y3};
        J4IBridge.polygon(g, xs, 0, ys, 0, 3, argbColor, false);
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor) {
        int[] xs = {x1, x2, x3}, ys = {y1, y2, y3};
        J4IBridge.polygon(g, xs, 0, ys, 0, 3, argbColor, true);
    }

    public void drawPolygon(int[] xPoints, int xOffset, int[] yPoints, int yOffset, int nPoints, int argbColor) {
        J4IBridge.polygon(g, xPoints, xOffset, yPoints, yOffset, nPoints, argbColor, false);
    }

    public void fillPolygon(int[] xPoints, int xOffset, int[] yPoints, int yOffset, int nPoints, int argbColor) {
        J4IBridge.polygon(g, xPoints, xOffset, yPoints, yOffset, nPoints, argbColor, true);
    }

    private void blit(int[] argb, int width, int height, int x, int y, int manipulation, boolean alpha) {
        int t = transform(manipulation);
        Image img = J4IBridge.wrap(argb, width, height, false);
        if (t == 0 && !alpha) {
            g.drawRGB(argb, 0, width, x, y, width, height, false);
            return;
        }
        if (!alpha) for (int i = 0; i < argb.length; i++) argb[i] |= 0xFF000000;
        g.drawRegion(img, 0, 0, width, height, t, x, y, Graphics.TOP | Graphics.LEFT);
    }

    public void drawPixels(int[] pixels, boolean transparency, int offset, int scanlength, int x, int y, int width, int height,
                           int manipulation, int format) {
        if (pixels == null) throw new NullPointerException();
        if (width <= 0 || height <= 0) return;
        int[] buf = new int[width * height];
        for (int r = 0; r < height; r++) System.arraycopy(pixels, offset + r * scanlength, buf, r * width, width);
        if (format == TYPE_INT_888_RGB) transparency = false;
        blit(buf, width, height, x, y, manipulation, transparency);
    }

    public void drawPixels(short[] pixels, boolean transparency, int offset, int scanlength, int x, int y, int width, int height,
                           int manipulation, int format) {
        if (pixels == null) throw new NullPointerException();
        if (width <= 0 || height <= 0) return;
        int[] buf = new int[width * height];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                int p = pixels[offset + r * scanlength + c] & 0xFFFF;
                int a = 0xFF, rr, gg, bb;
                switch (format) {
                case TYPE_USHORT_4444_ARGB:
                    a = ((p >> 12) & 15) * 17;
                    rr = ((p >> 8) & 15) * 17;
                    gg = ((p >> 4) & 15) * 17;
                    bb = (p & 15) * 17;
                    break;
                case TYPE_USHORT_444_RGB:
                    rr = ((p >> 8) & 15) * 17;
                    gg = ((p >> 4) & 15) * 17;
                    bb = (p & 15) * 17;
                    break;
                case TYPE_USHORT_1555_ARGB:
                    a = (p & 0x8000) != 0 ? 0xFF : 0;
                    rr = ((p >> 10) & 31) * 255 / 31;
                    gg = ((p >> 5) & 31) * 255 / 31;
                    bb = (p & 31) * 255 / 31;
                    break;
                case TYPE_USHORT_555_RGB:
                    rr = ((p >> 10) & 31) * 255 / 31;
                    gg = ((p >> 5) & 31) * 255 / 31;
                    bb = (p & 31) * 255 / 31;
                    break;
                default:
                    rr = ((p >> 11) & 31) * 255 / 31;
                    gg = ((p >> 5) & 63) * 255 / 63;
                    bb = (p & 31) * 255 / 31;
                    break;
                }
                if (!transparency) a = 0xFF;
                buf[r * width + c] = (a << 24) | (rr << 16) | (gg << 8) | bb;
            }
        }
        blit(buf, width, height, x, y, manipulation, transparency);
    }

    public void drawPixels(byte[] pixels, byte[] transparencyMask, int offset, int scanlength, int x, int y, int width, int height,
                           int manipulation, int format) {
        if (pixels == null) throw new NullPointerException();
        if (width <= 0 || height <= 0) return;
        int[] buf = new int[width * height];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                int v;
                boolean opaque = true;
                if (format == TYPE_BYTE_1_GRAY) {
                    int bit = offset * 8 + r * scanlength + c;
                    int b = pixels[bit >> 3] & 0xFF;
                    v = ((b >> (7 - (bit & 7))) & 1) != 0 ? 0x000000 : 0xFFFFFF;
                    if (transparencyMask != null) opaque = ((transparencyMask[bit >> 3] >> (7 - (bit & 7))) & 1) != 0;
                } else if (format == TYPE_BYTE_332_RGB) {
                    int p = pixels[offset + r * scanlength + c] & 0xFF;
                    v = (((p >> 5) & 7) * 255 / 7 << 16) | (((p >> 2) & 7) * 255 / 7 << 8) | ((p & 3) * 85);
                } else {
                    int p = pixels[offset + r * scanlength + c] & 0xFF;
                    v = (p << 16) | (p << 8) | p;
                }
                buf[r * width + c] = (opaque ? 0xFF000000 : 0) | v;
            }
        }
        blit(buf, width, height, x, y, manipulation, transparencyMask != null);
    }

    private int px(int x, int y) {
        int[] t = J4IBridge.targetPixels(g);
        int w = J4IBridge.targetWidth(g), h = t.length / w;
        x += g.getTranslateX();
        y += g.getTranslateY();
        if (x < 0 || y < 0 || x >= w || y >= h) throw new IllegalArgumentException();
        return t[y * w + x];
    }

    public void getPixels(int[] pixels, int offset, int scanlength, int x, int y, int width, int height, int format) {
        for (int r = 0; r < height; r++)
            for (int c = 0; c < width; c++) {
                int p = px(x + c, y + r);
                pixels[offset + r * scanlength + c] = format == TYPE_INT_888_RGB ? p & 0xFFFFFF : p;
            }
    }

    public void getPixels(byte[] pixels, byte[] transparencyMask, int offset, int scanlength, int x, int y, int width, int height, int format) {
        for (int r = 0; r < height; r++)
            for (int c = 0; c < width; c++) {
                int p = px(x + c, y + r);
                int gray = (((p >> 16) & 255) + ((p >> 8) & 255) + (p & 255)) / 3;
                pixels[offset + r * scanlength + c] = format == TYPE_BYTE_332_RGB
                    ? (byte) ((((p >> 21) & 7) << 5) | (((p >> 13) & 7) << 2) | ((p >> 6) & 3)) : (byte) gray;
            }
    }

    public void getPixels(short[] pixels, int offset, int scanlength, int x, int y, int width, int height, int format) {
        for (int r = 0; r < height; r++)
            for (int c = 0; c < width; c++) {
                int p = px(x + c, y + r);
                int a = p >>> 24, rr = (p >> 16) & 255, gg = (p >> 8) & 255, bb = p & 255;
                int v;
                switch (format) {
                case TYPE_USHORT_4444_ARGB: v = ((a >> 4) << 12) | ((rr >> 4) << 8) | ((gg >> 4) << 4) | (bb >> 4); break;
                case TYPE_USHORT_444_RGB: v = ((rr >> 4) << 8) | ((gg >> 4) << 4) | (bb >> 4); break;
                case TYPE_USHORT_1555_ARGB: v = (a >= 128 ? 0x8000 : 0) | ((rr >> 3) << 10) | ((gg >> 3) << 5) | (bb >> 3); break;
                case TYPE_USHORT_555_RGB: v = ((rr >> 3) << 10) | ((gg >> 3) << 5) | (bb >> 3); break;
                default: v = ((rr >> 3) << 11) | ((gg >> 2) << 5) | (bb >> 3); break;
                }
                pixels[offset + r * scanlength + c] = (short) v;
            }
    }

    public int getNativePixelFormat() { return TYPE_INT_8888_ARGB; }

    public int getAlphaComponent() { return (J4IBridge.getARGBColor(g) >>> 24) & 0xFF; }
}
