package javax.microedition.m3g;

public class Background extends Object3D {
    public static final int BORDER = 32;
    public static final int REPEAT = 33;

    int color;
    Image2D image;
    int modeX = BORDER, modeY = BORDER;
    int cropX, cropY, cropW, cropH;
    boolean colorClear = true, depthClear = true;

    public Background() {}

    Object3D dup() {
        Background b = new Background();
        b.color = color;
        b.image = image;
        b.modeX = modeX;
        b.modeY = modeY;
        b.cropX = cropX;
        b.cropY = cropY;
        b.cropW = cropW;
        b.cropH = cropH;
        b.colorClear = colorClear;
        b.depthClear = depthClear;
        return b;
    }

    int refs(Object3D[] out, int pos) { return add(out, super.refs(out, pos), image); }

    public void setColorClearEnable(boolean enable) { colorClear = enable; }

    public boolean isColorClearEnabled() { return colorClear; }

    public void setDepthClearEnable(boolean enable) { depthClear = enable; }

    public boolean isDepthClearEnabled() { return depthClear; }

    public void setColor(int argb) { color = argb; }

    public int getColor() { return color; }

    public void setImage(Image2D image) {
        if (image != null && image.format != Image2D.RGB && image.format != Image2D.RGBA) throw new IllegalArgumentException();
        this.image = image;
        if (image != null) {
            cropX = 0;
            cropY = 0;
            cropW = image.width;
            cropH = image.height;
        }
    }

    public Image2D getImage() { return image; }

    public void setImageMode(int modeX, int modeY) {
        if ((modeX != BORDER && modeX != REPEAT) || (modeY != BORDER && modeY != REPEAT)) throw new IllegalArgumentException();
        this.modeX = modeX;
        this.modeY = modeY;
    }

    public int getImageModeX() { return modeX; }

    public int getImageModeY() { return modeY; }

    public void setCrop(int x, int y, int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException();
        cropX = x;
        cropY = y;
        cropW = width;
        cropH = height;
    }

    public int getCropX() { return cropX; }

    public int getCropY() { return cropY; }

    public int getCropWidth() { return cropW; }

    public int getCropHeight() { return cropH; }

    boolean acceptsProperty(int p) {
        return p == AnimationTrack.ALPHA || p == AnimationTrack.COLOR || p == AnimationTrack.CROP || super.acceptsProperty(p);
    }

    void applyAnimation(int property, float[] v, int comps) {
        switch (property) {
            case AnimationTrack.ALPHA: color = (color & 0xFFFFFF) | (clamp255(v[0]) << 24); break;
            case AnimationTrack.COLOR: color = colorFrom(v, comps, color); break;
            case AnimationTrack.CROP:
                cropX = (int) v[0];
                cropY = (int) v[1];
                if (comps >= 4) {
                    cropW = Math.max(0, (int) v[2]);
                    cropH = Math.max(0, (int) v[3]);
                }
                break;
            default: super.applyAnimation(property, v, comps);
        }
    }
}
