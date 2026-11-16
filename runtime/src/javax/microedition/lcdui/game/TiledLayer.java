package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class TiledLayer extends Layer {
    final int cols, rows;
    Image image;
    int tw, th;
    int tileCount;
    final int[] cells;
    int[] anim = new int[4];
    int animCount;

    public TiledLayer(int columns, int rows, Image image, int tileWidth, int tileHeight) {
        super(columns * tileWidth, rows * tileHeight);
        if (columns < 1 || rows < 1 || tileWidth < 1 || tileHeight < 1 || image.getWidth() % tileWidth != 0
            || image.getHeight() % tileHeight != 0) throw new IllegalArgumentException();
        this.cols = columns;
        this.rows = rows;
        this.cells = new int[columns * rows];
        setTiles(image, tileWidth, tileHeight);
    }

    private void setTiles(Image img, int w, int h) {
        image = img;
        tw = w;
        th = h;
        tileCount = (img.getWidth() / w) * (img.getHeight() / h);
        width = cols * w;
        height = rows * h;
    }

    public int createAnimatedTile(int staticTileIndex) {
        if (staticTileIndex < 0 || staticTileIndex > tileCount) throw new IndexOutOfBoundsException();
        if (animCount == anim.length) {
            int[] n = new int[anim.length * 2];
            System.arraycopy(anim, 0, n, 0, animCount);
            anim = n;
        }
        anim[animCount++] = staticTileIndex;
        return -animCount;
    }

    public void setAnimatedTile(int animatedTileIndex, int staticTileIndex) {
        int i = -animatedTileIndex - 1;
        if (i < 0 || i >= animCount) throw new IndexOutOfBoundsException();
        if (staticTileIndex < 0 || staticTileIndex > tileCount) throw new IndexOutOfBoundsException();
        anim[i] = staticTileIndex;
    }

    public int getAnimatedTile(int animatedTileIndex) {
        int i = -animatedTileIndex - 1;
        if (i < 0 || i >= animCount) throw new IndexOutOfBoundsException();
        return anim[i];
    }

    public void setCell(int col, int row, int tileIndex) {
        if (col < 0 || col >= cols || row < 0 || row >= rows) throw new IndexOutOfBoundsException();
        if (tileIndex > tileCount || -tileIndex > animCount) throw new IndexOutOfBoundsException();
        cells[row * cols + col] = tileIndex;
    }

    public int getCell(int col, int row) {
        if (col < 0 || col >= cols || row < 0 || row >= rows) throw new IndexOutOfBoundsException();
        return cells[row * cols + col];
    }

    public void fillCells(int col, int row, int numCols, int numRows, int tileIndex) {
        if (numCols < 0 || numRows < 0 || col < 0 || row < 0 || col + numCols > cols || row + numRows > rows)
            throw new IndexOutOfBoundsException();
        if (tileIndex > tileCount || -tileIndex > animCount) throw new IndexOutOfBoundsException();
        for (int r = row; r < row + numRows; r++)
            for (int c = col; c < col + numCols; c++) cells[r * cols + c] = tileIndex;
    }

    public final int getCellWidth() { return tw; }

    public final int getCellHeight() { return th; }

    public final int getColumns() { return cols; }

    public final int getRows() { return rows; }

    public void setStaticTileSet(Image img, int tileWidth, int tileHeight) {
        if (tileWidth < 1 || tileHeight < 1 || img.getWidth() % tileWidth != 0 || img.getHeight() % tileHeight != 0)
            throw new IllegalArgumentException();
        int newCount = (img.getWidth() / tileWidth) * (img.getHeight() / tileHeight);
        boolean keep = newCount >= tileCount;
        setTiles(img, tileWidth, tileHeight);
        if (!keep) {
            for (int i = 0; i < cells.length; i++) cells[i] = 0;
            animCount = 0;
        }
    }

    int resolvedTile(int c, int r) {
        int t = cells[r * cols + c];
        if (t < 0) t = anim[-t - 1];
        return t;
    }

    public final void paint(Graphics g) {
        if (!visible) return;
        int perRow = image.getWidth() / tw;
        int clipX = g.getClipX(), clipY = g.getClipY();
        int c0 = Math.max(0, (clipX - x) / tw), r0 = Math.max(0, (clipY - y) / th);
        int c1 = Math.min(cols - 1, (clipX + g.getClipWidth() - 1 - x) / tw);
        int r1 = Math.min(rows - 1, (clipY + g.getClipHeight() - 1 - y) / th);
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                int t = resolvedTile(c, r);
                if (t <= 0) continue;
                t--;
                g.drawRegion(image, (t % perRow) * tw, (t / perRow) * th, tw, th, Sprite.TRANS_NONE, x + c * tw, y + r * th,
                             Graphics.TOP | Graphics.LEFT);
            }
        }
    }
}
