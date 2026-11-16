package com.siemens.mp.color_game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/** Siemens copy of the MIDP-2 TiledLayer, delegating to javax.microedition.lcdui.game.TiledLayer. */
public class TiledLayer extends Layer {
    final javax.microedition.lcdui.game.TiledLayer d;

    public TiledLayer(int columns, int rows, Image image, int tileWidth, int tileHeight) {
        super(columns * tileWidth, rows * tileHeight);
        d = new javax.microedition.lcdui.game.TiledLayer(columns, rows, image, tileWidth, tileHeight);
        width = d.getWidth();
        height = d.getHeight();
    }

    final void push() {
        d.setPosition(x, y);
        d.setVisible(visible);
    }

    public int createAnimatedTile(int staticTileIndex) { return d.createAnimatedTile(staticTileIndex); }

    public void setAnimatedTile(int animatedTileIndex, int staticTileIndex) { d.setAnimatedTile(animatedTileIndex, staticTileIndex); }

    public int getAnimatedTile(int animatedTileIndex) { return d.getAnimatedTile(animatedTileIndex); }

    public void setCell(int col, int row, int tileIndex) { d.setCell(col, row, tileIndex); }

    public int getCell(int col, int row) { return d.getCell(col, row); }

    public void fillCells(int col, int row, int numCols, int numRows, int tileIndex) {
        d.fillCells(col, row, numCols, numRows, tileIndex);
    }

    public final int getCellWidth() { return d.getCellWidth(); }

    public final int getCellHeight() { return d.getCellHeight(); }

    public final int getColumns() { return d.getColumns(); }

    public final int getRows() { return d.getRows(); }

    public void setStaticTileSet(Image image, int tileWidth, int tileHeight) {
        d.setStaticTileSet(image, tileWidth, tileHeight);
        width = d.getWidth();
        height = d.getHeight();
    }

    public final void paint(Graphics g) {
        if (!visible) return;
        push();
        d.paint(g);
    }
}
