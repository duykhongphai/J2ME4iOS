package com.mascotcapsule.micro3d.v3;

import java.io.IOException;

/** 8-bit palette BMP texture. Palette index 0 is the transparent color key. */
public class Texture {
    final boolean forModel;
    int width, height;
    int[] argb;
    boolean disposed;

    public Texture(byte[] b, boolean isForModel) {
        if (b == null) throw new NullPointerException();
        forModel = isForModel;
        try {
            decode(b);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RuntimeException("Bad BMP texture");
        }
    }

    public Texture(String name, boolean isForModel) throws IOException {
        this(Figure.readResource(name), isForModel);
    }

    /* kept public like the original: some games touch it reflectively; unused here */
    public final Object impl = null;

    public final void dispose() { disposed = true; }

    private static int u16(byte[] b, int o) { return (b[o] & 255) | ((b[o + 1] & 255) << 8); }

    private static int s32(byte[] b, int o) { return (b[o] & 255) | ((b[o + 1] & 255) << 8) | ((b[o + 2] & 255) << 16) | (b[o + 3] << 24); }

    private void decode(byte[] b) {
        if (b.length < 26 || b[0] != 'B' || b[1] != 'M') throw new RuntimeException("Not a BMP texture");
        int raster = s32(b, 10);
        int hdr = s32(b, 14);
        int w, h, bpp;
        boolean topDown = false;
        if (hdr == 12) {
            w = u16(b, 18);
            h = u16(b, 20);
            bpp = u16(b, 24);
        } else if (hdr >= 40) {
            w = s32(b, 18);
            h = s32(b, 22);
            bpp = u16(b, 28);
            int comp = s32(b, 30);
            if (comp != 0) throw new RuntimeException("Compressed BMP not supported");
            if (h < 0) {
                h = -h;
                topDown = true;
            }
        } else throw new RuntimeException("Unsupported BMP header " + hdr);
        if (bpp != 8) throw new RuntimeException("Only 8-bit BMP textures are supported");
        int palOff = 14 + hdr;
        int[] pal = new int[256];
        int entries = Math.min(256, (raster - palOff) / 4);
        for (int i = 0; i < entries; i++) {
            int o = palOff + i * 4;
            pal[i] = ((b[o + 2] & 255) << 16) | ((b[o + 1] & 255) << 8) | (b[o] & 255) | (i == 0 ? 0 : 0xFF000000);
        }
        int stride = (w + 3) & ~3;
        width = w;
        height = h;
        argb = new int[w * h];
        for (int y = 0; y < h; y++) {
            int row = raster + (topDown ? y : h - 1 - y) * stride;
            for (int x = 0; x < w; x++) argb[y * w + x] = pal[b[row + x] & 255];
        }
    }
}
