package com.siemens.mp.game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.J4IBridge;

/**
 * Siemens tiled background of 8x8 tiles. Map values: 0 = transparent, 1 = white, 2 = black,
 * n >= 3 = tile (n - 3) of the tile image (tiles stacked vertically, 8 pixels wide).
 */
public class TiledBackground extends GraphicObject {
    private static final int T = 8;
    private final Image tiles;
    private final int tileCount;
    private final byte[] map;
    private final int widthInTiles, heightInTiles;
    private int posx, posy;

    public TiledBackground(byte[] tilePixels, byte[] tileMask, byte[] map, int widthInTiles, int heightInTiles) {
        this(com.siemens.mp.ui.Image.fromBitmap(tilePixels, 0, tileMask, 0, T, Math.max(T, tilePixels.length)), (Image) null,
             map, widthInTiles, heightInTiles);
    }

    public TiledBackground(ExtendedImage tilePixels, ExtendedImage tileMask, byte[] map, int widthInTiles, int heightInTiles) {
        this(tilePixels.getImage(), tileMask == null ? null : tileMask.getImage(), map, widthInTiles, heightInTiles);
    }

    public TiledBackground(Image tilePixels, Image tileMask, byte[] map, int widthInTiles, int heightInTiles) {
        if (tilePixels == null || map == null) throw new NullPointerException();
        tiles = tileMask != null ? com.siemens.mp.ui.Image.createTransparentImageFromMask(tilePixels, tileMask) : tilePixels;
        tileCount = (tiles.getWidth() / T) * (tiles.getHeight() / T);
        this.map = map;
        this.widthInTiles = widthInTiles;
        this.heightInTiles = heightInTiles;
    }

    public void setPositionInMap(int x, int y) {
        posx = x;
        posy = y;
    }

    /** Map value at a pixel position in map coordinates, or 0 outside the map. */
    int tileAt(int mx, int my) {
        if (mx < 0 || my < 0) return 0;
        int c = mx / T, r = my / T;
        if (c >= widthInTiles || r >= heightInTiles) return 0;
        int i = r * widthInTiles + c;
        return i < map.length ? map[i] & 0xFF : 0;
    }

    protected void paint(Graphics g) {
        int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
        if (cw <= 0 || ch <= 0) return;
        int c0 = floorDiv(cx + posx, T), c1 = floorDiv(cx + cw - 1 + posx, T);
        int r0 = floorDiv(cy + posy, T), r1 = floorDiv(cy + ch - 1 + posy, T);
        if (c0 < 0) c0 = 0;
        if (r0 < 0) r0 = 0;
        if (c1 >= widthInTiles) c1 = widthInTiles - 1;
        if (r1 >= heightInTiles) r1 = heightInTiles - 1;
        int perRow = Math.max(1, tiles.getWidth() / T);
        int saved = g.getColor();
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                int i = r * widthInTiles + c;
                if (i >= map.length) continue;
                int v = map[i] & 0xFF;
                int dx = c * T - posx, dy = r * T - posy;
                if (v == 0) continue;
                if (v == 1 || v == 2) {
                    g.setColor(v == 1 ? 0xFFFFFF : 0x000000);
                    g.fillRect(dx, dy, T, T);
                    continue;
                }
                int t = v - 3;
                if (t >= tileCount) continue;
                g.drawRegion(tiles, (t % perRow) * T, (t / perRow) * T, T, T, 0, dx, dy, Graphics.TOP | Graphics.LEFT);
            }
        }
        g.setColor(saved);
    }

    private static int floorDiv(int a, int b) {
        int q = a / b;
        if ((a % b != 0) && ((a < 0) != (b < 0))) q--;
        return q;
    }

    /** Is the background opaque at map pixel (mx, my)? */
    boolean opaqueAt(int mx, int my) {
        int v = tileAt(mx, my);
        if (v == 0) return false;
        if (v < 3) return true;
        int t = v - 3;
        if (t >= tileCount) return false;
        int perRow = Math.max(1, tiles.getWidth() / T);
        int px = (t % perRow) * T + (mx & 7), py = (t / perRow) * T + (my & 7);
        return (J4IBridge.pixels(tiles)[py * tiles.getWidth() + px] >>> 24) != 0;
    }
}
