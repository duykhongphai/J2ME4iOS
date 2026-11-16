package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.J4IBridge;

public class Sprite extends Layer {
    public static final int TRANS_NONE = 0;
    public static final int TRANS_ROT90 = 5;
    public static final int TRANS_ROT180 = 3;
    public static final int TRANS_ROT270 = 6;
    public static final int TRANS_MIRROR = 2;
    public static final int TRANS_MIRROR_ROT90 = 7;
    public static final int TRANS_MIRROR_ROT180 = 1;
    public static final int TRANS_MIRROR_ROT270 = 4;

    Image image;
    int fw, fh;
    int rawFrames;
    int[] sequence;
    boolean customSequence;
    int frameIdx;
    int refX, refY;
    int transform;
    int colX, colY, colW, colH;

    public Sprite(Image image) {
        super(image.getWidth(), image.getHeight());
        init(image, image.getWidth(), image.getHeight());
    }

    public Sprite(Image image, int frameWidth, int frameHeight) {
        super(frameWidth, frameHeight);
        if (frameWidth <= 0 || frameHeight <= 0 || image.getWidth() % frameWidth != 0 || image.getHeight() % frameHeight != 0)
            throw new IllegalArgumentException();
        init(image, frameWidth, frameHeight);
    }

    public Sprite(Sprite s) {
        super(s.width, s.height);
        image = s.image;
        fw = s.fw;
        fh = s.fh;
        rawFrames = s.rawFrames;
        sequence = new int[s.sequence.length];
        System.arraycopy(s.sequence, 0, sequence, 0, sequence.length);
        customSequence = s.customSequence;
        frameIdx = s.frameIdx;
        refX = s.refX;
        refY = s.refY;
        transform = s.transform;
        colX = s.colX;
        colY = s.colY;
        colW = s.colW;
        colH = s.colH;
        x = s.x;
        y = s.y;
        visible = s.visible;
    }

    private void init(Image img, int w, int h) {
        image = img;
        fw = w;
        fh = h;
        rawFrames = (img.getWidth() / w) * (img.getHeight() / h);
        sequence = new int[rawFrames];
        for (int i = 0; i < rawFrames; i++) sequence[i] = i;
        customSequence = false;
        frameIdx = 0;
        colX = colY = 0;
        colW = w;
        colH = h;
        width = transform >= 4 ? h : w;
        height = transform >= 4 ? w : h;
    }

    /* transformed position of a point (px,py) inside an untransformed frame */
    private int tx(int px, int py) {
        switch (transform) {
        case TRANS_MIRROR: case TRANS_ROT180: return fw - 1 - px;
        case TRANS_ROT90: case TRANS_MIRROR_ROT90: return fh - 1 - py;
        case TRANS_ROT270: case TRANS_MIRROR_ROT270: return py;
        default: return px;
        }
    }

    private int ty(int px, int py) {
        switch (transform) {
        case TRANS_MIRROR_ROT180: case TRANS_ROT180: return fh - 1 - py;
        case TRANS_ROT90: case TRANS_MIRROR_ROT270: return px;
        case TRANS_ROT270: case TRANS_MIRROR_ROT90: return fw - 1 - px;
        default: return py;
        }
    }

    public void defineReferencePixel(int x, int y) {
        refX = x;
        refY = y;
    }

    public void setRefPixelPosition(int x, int y) {
        this.x = x - tx(refX, refY);
        this.y = y - ty(refX, refY);
    }

    public int getRefPixelX() { return x + tx(refX, refY); }

    public int getRefPixelY() { return y + ty(refX, refY); }

    public void setFrame(int sequenceIndex) {
        if (sequenceIndex < 0 || sequenceIndex >= sequence.length) throw new IndexOutOfBoundsException();
        frameIdx = sequenceIndex;
    }

    public final int getFrame() { return frameIdx; }

    public int getRawFrameCount() { return rawFrames; }

    public int getFrameSequenceLength() { return sequence.length; }

    public void nextFrame() { frameIdx = (frameIdx + 1) % sequence.length; }

    public void prevFrame() { frameIdx = (frameIdx + sequence.length - 1) % sequence.length; }

    public void setFrameSequence(int[] seq) {
        if (seq == null) {
            sequence = new int[rawFrames];
            for (int i = 0; i < rawFrames; i++) sequence[i] = i;
            customSequence = false;
        } else {
            if (seq.length < 1) throw new IllegalArgumentException();
            for (int i = 0; i < seq.length; i++) if (seq[i] < 0 || seq[i] >= rawFrames) throw new ArrayIndexOutOfBoundsException();
            sequence = new int[seq.length];
            System.arraycopy(seq, 0, sequence, 0, seq.length);
            customSequence = true;
        }
        frameIdx = 0;
    }

    public void setImage(Image img, int frameWidth, int frameHeight) {
        if (img == null) throw new NullPointerException();
        if (frameWidth <= 0 || frameHeight <= 0 || img.getWidth() % frameWidth != 0 || img.getHeight() % frameHeight != 0)
            throw new IllegalArgumentException();
        int oldRefX = getRefPixelX(), oldRefY = getRefPixelY();
        int newRaw = (img.getWidth() / frameWidth) * (img.getHeight() / frameHeight);
        boolean keepSeq = customSequence && newRaw >= rawFrames;
        int[] seq = sequence;
        int idx = frameIdx;
        boolean sizeSame = frameWidth == fw && frameHeight == fh;
        int cx = colX, cy = colY, cw = colW, ch = colH;
        init(img, frameWidth, frameHeight);
        if (keepSeq) {
            sequence = seq;
            customSequence = true;
            frameIdx = idx;
        }
        if (sizeSame) {
            colX = cx;
            colY = cy;
            colW = cw;
            colH = ch;
        }
        setRefPixelPosition(oldRefX, oldRefY);
    }

    public void defineCollisionRectangle(int x, int y, int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException();
        colX = x;
        colY = y;
        colW = width;
        colH = height;
    }

    public void setTransform(int t) {
        if (t < 0 || t > 7) throw new IllegalArgumentException();
        int rx = getRefPixelX(), ry = getRefPixelY();
        transform = t;
        width = t >= 4 ? fh : fw;
        height = t >= 4 ? fw : fh;
        setRefPixelPosition(rx, ry);
    }

    public final void paint(Graphics g) {
        if (!visible) return;
        int f = sequence[frameIdx];
        int perRow = image.getWidth() / fw;
        g.drawRegion(image, (f % perRow) * fw, (f / perRow) * fh, fw, fh, transform, x, y, Graphics.TOP | Graphics.LEFT);
    }

    /* ---------- collisions ---------- */
    /** collision rectangle in screen coordinates: {x, y, w, h} */
    int[] colRect() {
        int cx, cy, cw, ch;
        switch (transform) {
        case TRANS_MIRROR: cx = fw - colX - colW; cy = colY; cw = colW; ch = colH; break;
        case TRANS_ROT180: cx = fw - colX - colW; cy = fh - colY - colH; cw = colW; ch = colH; break;
        case TRANS_MIRROR_ROT180: cx = colX; cy = fh - colY - colH; cw = colW; ch = colH; break;
        case TRANS_ROT90: cx = fh - colY - colH; cy = colX; cw = colH; ch = colW; break;
        case TRANS_ROT270: cx = colY; cy = fw - colX - colW; cw = colH; ch = colW; break;
        case TRANS_MIRROR_ROT90: cx = fh - colY - colH; cy = fw - colX - colW; cw = colH; ch = colW; break;
        case TRANS_MIRROR_ROT270: cx = colY; cy = colX; cw = colH; ch = colW; break;
        default: cx = colX; cy = colY; cw = colW; ch = colH; break;
        }
        return new int[] {x + cx, y + cy, cw, ch};
    }

    /** Is the sprite pixel at screen (sx, sy) opaque? */
    boolean opaqueAt(int sx, int sy) {
        int u = sx - x, v = sy - y;
        int i, j;
        switch (transform) {
        case TRANS_MIRROR: i = fw - 1 - u; j = v; break;
        case TRANS_ROT180: i = fw - 1 - u; j = fh - 1 - v; break;
        case TRANS_MIRROR_ROT180: i = u; j = fh - 1 - v; break;
        case TRANS_ROT90: i = v; j = fh - 1 - u; break;
        case TRANS_ROT270: i = fw - 1 - v; j = u; break;
        case TRANS_MIRROR_ROT90: i = fw - 1 - v; j = fh - 1 - u; break;
        case TRANS_MIRROR_ROT270: i = v; j = u; break;
        default: i = u; j = v; break;
        }
        if (i < 0 || j < 0 || i >= fw || j >= fh) return false;
        int f = sequence[frameIdx];
        int perRow = image.getWidth() / fw;
        int px = (f % perRow) * fw + i, py = (f / perRow) * fh + j;
        return (J4IBridge.pixels(image)[py * image.getWidth() + px] >>> 24) != 0;
    }

    static boolean imageOpaque(Image img, int px, int py) {
        if (px < 0 || py < 0 || px >= img.getWidth() || py >= img.getHeight()) return false;
        return (J4IBridge.pixels(img)[py * img.getWidth() + px] >>> 24) != 0;
    }

    public final boolean collidesWith(Sprite s, boolean pixelLevel) {
        if (!visible || !s.visible) return false;
        int[] a = colRect(), b = s.colRect();
        int x0 = Math.max(a[0], b[0]), y0 = Math.max(a[1], b[1]);
        int x1 = Math.min(a[0] + a[2], b[0] + b[2]), y1 = Math.min(a[1] + a[3], b[1] + b[3]);
        if (x0 >= x1 || y0 >= y1) return false;
        if (!pixelLevel) return true;
        for (int yy = y0; yy < y1; yy++)
            for (int xx = x0; xx < x1; xx++)
                if (opaqueAt(xx, yy) && s.opaqueAt(xx, yy)) return true;
        return false;
    }

    public final boolean collidesWith(TiledLayer t, boolean pixelLevel) {
        if (!visible || !t.visible) return false;
        int[] a = colRect();
        int x0 = Math.max(a[0], t.x), y0 = Math.max(a[1], t.y);
        int x1 = Math.min(a[0] + a[2], t.x + t.width), y1 = Math.min(a[1] + a[3], t.y + t.height);
        if (x0 >= x1 || y0 >= y1) return false;
        int c0 = (x0 - t.x) / t.tw, c1 = (x1 - 1 - t.x) / t.tw;
        int r0 = (y0 - t.y) / t.th, r1 = (y1 - 1 - t.y) / t.th;
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                int tile = t.resolvedTile(c, r);
                if (tile == 0) continue;
                if (!pixelLevel) return true;
                int perRow = t.image.getWidth() / t.tw;
                int tsx = ((tile - 1) % perRow) * t.tw, tsy = ((tile - 1) / perRow) * t.th;
                int cx0 = Math.max(x0, t.x + c * t.tw), cx1 = Math.min(x1, t.x + (c + 1) * t.tw);
                int cy0 = Math.max(y0, t.y + r * t.th), cy1 = Math.min(y1, t.y + (r + 1) * t.th);
                for (int yy = cy0; yy < cy1; yy++)
                    for (int xx = cx0; xx < cx1; xx++)
                        if (opaqueAt(xx, yy) && imageOpaque(t.image, tsx + xx - t.x - c * t.tw, tsy + yy - t.y - r * t.th)) return true;
            }
        }
        return false;
    }

    public final boolean collidesWith(Image img, int ix, int iy, boolean pixelLevel) {
        if (!visible) return false;
        int[] a = colRect();
        int x0 = Math.max(a[0], ix), y0 = Math.max(a[1], iy);
        int x1 = Math.min(a[0] + a[2], ix + img.getWidth()), y1 = Math.min(a[1] + a[3], iy + img.getHeight());
        if (x0 >= x1 || y0 >= y1) return false;
        if (!pixelLevel) return true;
        for (int yy = y0; yy < y1; yy++)
            for (int xx = x0; xx < x1; xx++)
                if (opaqueAt(xx, yy) && imageOpaque(img, xx - ix, yy - iy)) return true;
        return false;
    }
}
