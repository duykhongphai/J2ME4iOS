package javax.microedition.m3g;

public class Sprite3D extends Node {
    boolean scaled;
    Image2D image;
    Appearance appearance;
    int cropX, cropY, cropW, cropH;

    public Sprite3D(boolean scaled, Image2D image, Appearance appearance) {
        this.scaled = scaled;
        setImage(image);
        this.appearance = appearance;
    }

    private Sprite3D() {}

    Object3D dup() {
        Sprite3D s = new Sprite3D();
        copyNodeTo(s);
        s.scaled = scaled;
        s.image = image;
        s.appearance = appearance;
        s.cropX = cropX;
        s.cropY = cropY;
        s.cropW = cropW;
        s.cropH = cropH;
        return s;
    }

    int refs(Object3D[] out, int pos) {
        pos = super.refs(out, pos);
        pos = add(out, pos, image);
        return add(out, pos, appearance);
    }

    public boolean isScaled() { return scaled; }

    public void setAppearance(Appearance appearance) { this.appearance = appearance; }

    public Appearance getAppearance() { return appearance; }

    public void setImage(Image2D image) {
        if (image == null) throw new NullPointerException();
        this.image = image;
        cropX = 0;
        cropY = 0;
        cropW = Math.min(image.width, 1024);
        cropH = Math.min(image.height, 1024);
    }

    public Image2D getImage() { return image; }

    public void setCrop(int cropX, int cropY, int width, int height) {
        if (Math.abs(width) > 1024 || Math.abs(height) > 1024) throw new IllegalArgumentException();
        this.cropX = cropX;
        this.cropY = cropY;
        cropW = width;
        cropH = height;
    }

    public int getCropX() { return cropX; }

    public int getCropY() { return cropY; }

    public int getCropWidth() { return cropW; }

    public int getCropHeight() { return cropH; }

    boolean acceptsProperty(int p) { return p == AnimationTrack.CROP || super.acceptsProperty(p); }

    void applyAnimation(int property, float[] v, int comps) {
        if (property == AnimationTrack.CROP) {
            cropX = (int) v[0];
            cropY = (int) v[1];
            if (comps >= 4) {
                cropW = (int) v[2];
                cropH = (int) v[3];
            }
        } else super.applyAnimation(property, v, comps);
    }
}
