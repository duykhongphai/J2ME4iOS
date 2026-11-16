package javax.microedition.lcdui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Image {
    int[] pix;
    int w, h;
    boolean mutable;

    Image(int[] pix, int w, int h, boolean mutable) {
        this.pix = pix;
        this.w = w;
        this.h = h;
        this.mutable = mutable;
    }

    public static Image createImage(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException();
        int[] p = new int[width * height];
        for (int i = 0; i < p.length; i++) p[i] = 0xFFFFFFFF;
        return new Image(p, width, height, true);
    }

    /** Nokia DirectUtils: mutable image with an initial ARGB fill. */
    static Image createARGB(int width, int height, int argb) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException();
        int[] p = new int[width * height];
        if (argb != 0) for (int i = 0; i < p.length; i++) p[i] = argb;
        return new Image(p, width, height, true);
    }

    public static Image createImage(Image source) {
        if (source == null) throw new NullPointerException();
        if (!source.mutable) return source;
        int[] p = new int[source.pix.length];
        System.arraycopy(source.pix, 0, p, 0, p.length);
        return new Image(p, source.w, source.h, false);
    }

    public static Image createImage(String name) throws IOException {
        if (name == null) throw new NullPointerException();
        String path = name.startsWith("/") ? name.substring(1) : name;
        byte[] data = j4i.Sys.getResource(path);
        if (data == null) throw new IOException("Resource not found: " + name);
        Image img = decode(data, 0, data.length);
        if (img == null) throw new IOException("Cannot decode image: " + name);
        return img;
    }

    public static Image createImage(byte[] imageData, int imageOffset, int imageLength) {
        if (imageData == null) throw new NullPointerException();
        if (imageOffset < 0 || imageLength < 0 || imageOffset + imageLength > imageData.length) throw new ArrayIndexOutOfBoundsException();
        Image img = decode(imageData, imageOffset, imageLength);
        if (img == null) throw new IllegalArgumentException("Cannot decode image");
        return img;
    }

    public static Image createImage(InputStream stream) throws IOException {
        if (stream == null) throw new NullPointerException();
        ByteArrayOutputStream bo = new ByteArrayOutputStream(4096);
        byte[] buf = new byte[4096];
        int n;
        while ((n = stream.read(buf, 0, buf.length)) > 0) bo.write(buf, 0, n);
        byte[] data = bo.toByteArray();
        Image img = decode(data, 0, data.length);
        if (img == null) throw new IOException("Cannot decode image");
        return img;
    }

    static Image decode(byte[] data, int off, int len) {
        int[] info = new int[3];
        int[] p = j4i.Gfx.decode(data, off, len, info);
        if (p == null) return null;
        return new Image(p, info[0], info[1], false);
    }

    public static Image createImage(Image image, int x, int y, int width, int height, int transform) {
        if (image == null) throw new NullPointerException();
        if (x < 0 || y < 0 || width <= 0 || height <= 0 || x + width > image.w || y + height > image.h)
            throw new IllegalArgumentException("region");
        if (transform < 0 || transform > 7) throw new IllegalArgumentException("transform");
        int nw = transform >= 4 ? height : width, nh = transform >= 4 ? width : height;
        int[] p = new int[nw * nh];
        j4i.Gfx.drawRegion(p, nw, nh, 0, 0, nw, nh, image.pix, image.w, image.h, x, y, width, height, transform, 0, 0);
        return new Image(p, nw, nh, false);
    }

    public static Image createRGBImage(int[] rgb, int width, int height, boolean processAlpha) {
        if (rgb == null) throw new NullPointerException();
        if (width <= 0 || height <= 0) throw new IllegalArgumentException();
        if (width * height > rgb.length) throw new ArrayIndexOutOfBoundsException();
        int[] p = new int[width * height];
        if (processAlpha) System.arraycopy(rgb, 0, p, 0, p.length);
        else for (int i = 0; i < p.length; i++) p[i] = rgb[i] | 0xFF000000;
        return new Image(p, width, height, false);
    }

    public Graphics getGraphics() {
        if (!mutable) throw new IllegalStateException("Image is immutable");
        return new Graphics(pix, w, h, this);
    }

    public int getWidth() { return w; }

    public int getHeight() { return h; }

    public boolean isMutable() { return mutable; }

    public void getRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height) {
        if (rgbData == null) throw new NullPointerException();
        if (x < 0 || y < 0 || x + width > w || y + height > h) throw new IllegalArgumentException();
        if (Math.abs(scanlength) < width) throw new IllegalArgumentException();
        for (int r = 0; r < height; r++) System.arraycopy(pix, (y + r) * w + x, rgbData, offset + r * scanlength, width);
    }
}
