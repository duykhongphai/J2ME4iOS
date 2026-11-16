package javax.microedition.lcdui;

import j4i.Gfx;

public class Graphics {
    public static final int HCENTER = 1;
    public static final int VCENTER = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int TOP = 16;
    public static final int BOTTOM = 32;
    public static final int BASELINE = 64;
    public static final int SOLID = 0;
    public static final int DOTTED = 1;

    int[] pix;
    int pw, ph;
    Image img; /* null when drawing to the display */

    int tx, ty;
    int cx0, cy0, cx1, cy1; /* absolute clip */
    int color;
    int argb = 0xFF000000;
    Font font;
    int stroke;

    Graphics(int[] pix, int w, int h, Image img) {
        this.pix = pix;
        this.pw = w;
        this.ph = h;
        this.img = img;
        reset();
    }

    void reset() {
        tx = ty = 0;
        cx0 = cy0 = 0;
        cx1 = pw;
        cy1 = ph;
        color = 0;
        argb = 0xFF000000;
        font = Font.getDefaultFont();
        stroke = SOLID;
    }

    /* ---------- state ---------- */
    public void translate(int x, int y) {
        tx += x;
        ty += y;
    }

    public int getTranslateX() { return tx; }

    public int getTranslateY() { return ty; }

    public int getColor() { return color; }

    public int getRedComponent() { return (color >> 16) & 0xff; }

    public int getGreenComponent() { return (color >> 8) & 0xff; }

    public int getBlueComponent() { return color & 0xff; }

    public int getGrayScale() { return (getRedComponent() * 76 + getGreenComponent() * 150 + getBlueComponent() * 29) >> 8; }

    public void setColor(int red, int green, int blue) {
        if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) throw new IllegalArgumentException();
        setColor((red << 16) | (green << 8) | blue);
    }

    public void setColor(int rgb) {
        color = rgb & 0xFFFFFF;
        argb = 0xFF000000 | color;
    }

    /** Nokia DirectGraphics support. */
    void setARGB(int c) {
        color = c & 0xFFFFFF;
        argb = c;
    }

    public void setGrayScale(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException();
        setColor(value, value, value);
    }

    public Font getFont() { return font; }

    public void setFont(Font f) { font = f == null ? Font.getDefaultFont() : f; }

    public void setStrokeStyle(int style) {
        if (style != SOLID && style != DOTTED) throw new IllegalArgumentException();
        stroke = style;
    }

    public int getStrokeStyle() { return stroke; }

    public int getDisplayColor(int c) { return c & 0xFFFFFF; }

    /* ---------- clip ---------- */
    public int getClipX() { return cx0 - tx; }

    public int getClipY() { return cy0 - ty; }

    public int getClipWidth() { return cx1 - cx0; }

    public int getClipHeight() { return cy1 - cy0; }

    public void clipRect(int x, int y, int w, int h) {
        int x0 = x + tx, y0 = y + ty;
        int x1 = (int) Math.min((long) x0 + w, Integer.MAX_VALUE), y1 = (int) Math.min((long) y0 + h, Integer.MAX_VALUE);
        if (x0 > cx0) cx0 = x0;
        if (y0 > cy0) cy0 = y0;
        if (x1 < cx1) cx1 = x1;
        if (y1 < cy1) cy1 = y1;
        if (cx1 < cx0) cx1 = cx0;
        if (cy1 < cy0) cy1 = cy0;
    }

    public void setClip(int x, int y, int w, int h) {
        int x0 = x + tx, y0 = y + ty;
        int x1 = (int) Math.min((long) x0 + w, Integer.MAX_VALUE), y1 = (int) Math.min((long) y0 + h, Integer.MAX_VALUE);
        if (x0 < 0) x0 = 0;
        if (y0 < 0) y0 = 0;
        if (x1 > pw) x1 = pw;
        if (y1 > ph) y1 = ph;
        if (x1 < x0) x1 = x0;
        if (y1 < y0) y1 = y0;
        cx0 = x0;
        cy0 = y0;
        cx1 = x1;
        cy1 = y1;
    }

    /* ---------- primitives ---------- */
    public void drawLine(int x1, int y1, int x2, int y2) {
        Gfx.drawLine(pix, pw, ph, cx0, cy0, cx1, cy1, argb, stroke == DOTTED, x1 + tx, y1 + ty, x2 + tx, y2 + ty);
    }

    public void fillRect(int x, int y, int width, int height) {
        Gfx.fillRect(pix, pw, ph, cx0, cy0, cx1, cy1, argb, x + tx, y + ty, width, height);
    }

    public void drawRect(int x, int y, int width, int height) {
        if (width < 0 || height < 0) return;
        if (stroke == SOLID) {
            int ax = x + tx, ay = y + ty;
            if (width == 0 || height == 0) {
                Gfx.fillRect(pix, pw, ph, cx0, cy0, cx1, cy1, argb, ax, ay, width + 1, height + 1);
                return;
            }
            Gfx.fillRect(pix, pw, ph, cx0, cy0, cx1, cy1, argb, ax, ay, width, 1);
            Gfx.fillRect(pix, pw, ph, cx0, cy0, cx1, cy1, argb, ax + width, ay, 1, height);
            Gfx.fillRect(pix, pw, ph, cx0, cy0, cx1, cy1, argb, ax + 1, ay + height, width, 1);
            Gfx.fillRect(pix, pw, ph, cx0, cy0, cx1, cy1, argb, ax, ay + 1, 1, height);
        } else {
            drawLine(x, y, x + width, y);
            drawLine(x + width, y, x + width, y + height);
            drawLine(x + width, y + height, x, y + height);
            drawLine(x, y + height, x, y);
        }
    }

    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        Gfx.roundRect(pix, pw, ph, cx0, cy0, cx1, cy1, argb, false, stroke == DOTTED, x + tx, y + ty, width, height, arcWidth, arcHeight);
    }

    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        Gfx.roundRect(pix, pw, ph, cx0, cy0, cx1, cy1, argb, true, false, x + tx, y + ty, width, height, arcWidth, arcHeight);
    }

    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        Gfx.arc(pix, pw, ph, cx0, cy0, cx1, cy1, argb, true, false, x + tx, y + ty, width, height, startAngle, arcAngle);
    }

    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        Gfx.arc(pix, pw, ph, cx0, cy0, cx1, cy1, argb, false, stroke == DOTTED, x + tx, y + ty, width, height, startAngle, arcAngle);
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        Gfx.fillTriangle(pix, pw, ph, cx0, cy0, cx1, cy1, argb, x1 + tx, y1 + ty, x2 + tx, y2 + ty, x3 + tx, y3 + ty);
    }

    void polygon(int[] xs, int xo, int[] ys, int yo, int n, int c, boolean fill) {
        Gfx.polygon(pix, pw, ph, cx0, cy0, cx1, cy1, c, xs, xo, ys, yo, n, tx, ty, fill);
    }

    /* ---------- text ---------- */
    public void drawString(String str, int x, int y, int anchor) {
        if (str == null) throw new NullPointerException();
        drawSubstring(str, 0, str.length(), x, y, anchor);
    }

    public void drawSubstring(String str, int offset, int len, int x, int y, int anchor) {
        if (str == null) throw new NullPointerException();
        if (offset < 0 || len < 0 || offset + len > str.length()) throw new StringIndexOutOfBoundsException();
        Font f = font;
        if ((anchor & (HCENTER | RIGHT)) != 0) {
            int w = f.substringWidth(str, offset, len);
            if ((anchor & HCENTER) != 0) x -= w / 2;
            else x -= w;
        }
        if ((anchor & BASELINE) != 0) y -= f.getBaselinePosition();
        else if ((anchor & BOTTOM) != 0) y -= f.getHeight();
        else if ((anchor & VCENTER) != 0) y -= f.getHeight() / 2;
        Gfx.drawString(pix, pw, ph, cx0, cy0, cx1, cy1, argb, str, offset, len, x + tx, y + ty, f.px, f.style);
    }

    public void drawChar(char character, int x, int y, int anchor) { drawString(String.valueOf(character), x, y, anchor); }

    public void drawChars(char[] data, int offset, int length, int x, int y, int anchor) {
        if (offset < 0 || length < 0 || offset + length > data.length) throw new ArrayIndexOutOfBoundsException();
        drawString(new String(data, offset, length), x, y, anchor);
    }

    /* ---------- images ---------- */
    public void drawImage(Image image, int x, int y, int anchor) {
        if (image == null) throw new NullPointerException();
        int w = image.w, h = image.h;
        if ((anchor & HCENTER) != 0) x -= w / 2;
        else if ((anchor & RIGHT) != 0) x -= w;
        if ((anchor & VCENTER) != 0) y -= h / 2;
        else if ((anchor & BOTTOM) != 0) y -= h;
        Gfx.drawRegion(pix, pw, ph, cx0, cy0, cx1, cy1, image.pix, w, h, 0, 0, w, h, 0, x + tx, y + ty);
    }

    public void drawRegion(Image src, int x_src, int y_src, int width, int height, int transform, int x_dest, int y_dest, int anchor) {
        if (src == null) throw new NullPointerException();
        if (src == img && img != null) throw new IllegalArgumentException("source is destination");
        if (transform < 0 || transform > 7) throw new IllegalArgumentException("transform");
        if (x_src < 0 || y_src < 0 || width < 0 || height < 0 || x_src + width > src.w || y_src + height > src.h)
            throw new IllegalArgumentException("region");
        int w = transform >= 4 ? height : width;
        int h = transform >= 4 ? width : height;
        if ((anchor & HCENTER) != 0) x_dest -= w / 2;
        else if ((anchor & RIGHT) != 0) x_dest -= w;
        if ((anchor & VCENTER) != 0) y_dest -= h / 2;
        else if ((anchor & BOTTOM) != 0) y_dest -= h;
        Gfx.drawRegion(pix, pw, ph, cx0, cy0, cx1, cy1, src.pix, src.w, src.h, x_src, y_src, width, height, transform, x_dest + tx, y_dest + ty);
    }

    public void drawRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height, boolean processAlpha) {
        if (rgbData == null) throw new NullPointerException();
        Gfx.drawRGB(pix, pw, ph, cx0, cy0, cx1, cy1, rgbData, offset, scanlength, x + tx, y + ty, width, height, processAlpha);
    }

    public void copyArea(int x_src, int y_src, int width, int height, int x_dest, int y_dest, int anchor) {
        if (img == null) throw new IllegalStateException();
        int sx = x_src + tx, sy = y_src + ty;
        if ((anchor & HCENTER) != 0) x_dest -= width / 2;
        else if ((anchor & RIGHT) != 0) x_dest -= width;
        if ((anchor & VCENTER) != 0) y_dest -= height / 2;
        else if ((anchor & BOTTOM) != 0) y_dest -= height;
        Gfx.copyArea(pix, pw, ph, cx0, cy0, cx1, cy1, sx, sy, width, height, x_dest + tx, y_dest + ty);
    }
}
