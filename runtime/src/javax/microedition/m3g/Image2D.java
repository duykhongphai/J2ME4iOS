package javax.microedition.m3g;

import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.J4IBridge;

public class Image2D extends Object3D {
    public static final int ALPHA = 96;
    public static final int LUMINANCE = 97;
    public static final int LUMINANCE_ALPHA = 98;
    public static final int RGB = 99;
    public static final int RGBA = 100;

    int format, width, height;
    boolean mutable;
    int[] argb; /* rendering representation, see formatBits() */

    private Image2D() {}

    public Image2D(int format, Object image) {
        checkFormat(format);
        if (image == null) throw new NullPointerException();
        if (!(image instanceof Image)) throw new IllegalArgumentException("Not an Image");
        Image img = (Image) image;
        this.format = format;
        width = img.getWidth();
        height = img.getHeight();
        int[] src = J4IBridge.pixels(img);
        argb = new int[width * height];
        for (int i = 0; i < argb.length; i++) argb[i] = convert(src[i]);
    }

    public Image2D(int format, int width, int height, byte[] image) {
        checkFormat(format);
        if (image == null) throw new NullPointerException();
        if (width <= 0 || height <= 0) throw new IllegalArgumentException();
        int bpp = bpp(format);
        if (image.length < width * height * bpp) throw new IllegalArgumentException();
        this.format = format;
        this.width = width;
        this.height = height;
        argb = new int[width * height];
        for (int i = 0; i < argb.length; i++) argb[i] = fromBytes(image, i * bpp);
    }

    public Image2D(int format, int width, int height, byte[] image, byte[] palette) {
        checkFormat(format);
        if (image == null || palette == null) throw new NullPointerException();
        if (width <= 0 || height <= 0) throw new IllegalArgumentException();
        int bpp = bpp(format);
        if (image.length < width * height || palette.length < bpp) throw new IllegalArgumentException();
        this.format = format;
        this.width = width;
        this.height = height;
        int entries = Math.min(256, palette.length / bpp);
        int[] pal = new int[256];
        for (int i = 0; i < entries; i++) pal[i] = fromBytes(palette, i * bpp);
        argb = new int[width * height];
        for (int i = 0; i < argb.length; i++) argb[i] = pal[image[i] & 255];
    }

    public Image2D(int format, int width, int height) {
        checkFormat(format);
        if (width <= 0 || height <= 0) throw new IllegalArgumentException();
        this.format = format;
        this.width = width;
        this.height = height;
        mutable = true;
        argb = new int[width * height];
        for (int i = 0; i < argb.length; i++) argb[i] = 0xFFFFFFFF;
    }

    Object3D dup() {
        Image2D c = new Image2D();
        c.format = format;
        c.width = width;
        c.height = height;
        c.mutable = mutable;
        c.argb = new int[argb.length];
        System.arraycopy(argb, 0, c.argb, 0, argb.length);
        return c;
    }

    static void checkFormat(int f) {
        if (f < ALPHA || f > RGBA) throw new IllegalArgumentException("Bad image format " + f);
    }

    static int bpp(int f) {
        switch (f) {
            case ALPHA:
            case LUMINANCE: return 1;
            case LUMINANCE_ALPHA: return 2;
            case RGB: return 3;
            default: return 4;
        }
    }

    /** bit 1: has color, bit 2: has alpha */
    int formatBits() {
        switch (format) {
            case ALPHA: return 2;
            case LUMINANCE: return 1;
            case RGB: return 1;
            default: return 3;
        }
    }

    private int fromBytes(byte[] b, int o) {
        switch (format) {
            case ALPHA: return ((b[o] & 255) << 24) | 0xFFFFFF;
            case LUMINANCE: {
                int l = b[o] & 255;
                return 0xFF000000 | (l << 16) | (l << 8) | l;
            }
            case LUMINANCE_ALPHA: {
                int l = b[o] & 255;
                return ((b[o + 1] & 255) << 24) | (l << 16) | (l << 8) | l;
            }
            case RGB: return 0xFF000000 | ((b[o] & 255) << 16) | ((b[o + 1] & 255) << 8) | (b[o + 2] & 255);
            default: return ((b[o + 3] & 255) << 24) | ((b[o] & 255) << 16) | ((b[o + 1] & 255) << 8) | (b[o + 2] & 255);
        }
    }

    private int convert(int p) {
        int a = p >>> 24, r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
        int l = (r * 77 + g * 150 + b * 29) >> 8;
        switch (format) {
            case ALPHA: return (a << 24) | 0xFFFFFF;
            case LUMINANCE: return 0xFF000000 | (l << 16) | (l << 8) | l;
            case LUMINANCE_ALPHA: return (a << 24) | (l << 16) | (l << 8) | l;
            case RGB: return 0xFF000000 | (p & 0xFFFFFF);
            default: return p;
        }
    }

    public void set(int x, int y, int width, int height, byte[] image) {
        if (image == null) throw new NullPointerException();
        if (!mutable) throw new IllegalStateException("Image2D is immutable");
        if (x < 0 || y < 0 || width <= 0 || height <= 0 || x + width > this.width || y + height > this.height)
            throw new IllegalArgumentException();
        int bpp = bpp(format);
        if (image.length < width * height * bpp) throw new IllegalArgumentException();
        for (int j = 0; j < height; j++)
            for (int i = 0; i < width; i++) argb[(y + j) * this.width + x + i] = fromBytes(image, (j * width + i) * bpp);
    }

    public boolean isMutable() { return mutable; }

    public int getFormat() { return format; }

    public int getWidth() { return width; }

    public int getHeight() { return height; }
}
