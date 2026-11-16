package javax.microedition.m3g;

public class Texture2D extends Transformable {
    public static final int FILTER_BASE_LEVEL = 208;
    public static final int FILTER_LINEAR = 209;
    public static final int FILTER_NEAREST = 210;
    public static final int FUNC_ADD = 224;
    public static final int FUNC_BLEND = 225;
    public static final int FUNC_DECAL = 226;
    public static final int FUNC_MODULATE = 227;
    public static final int FUNC_REPLACE = 228;
    public static final int WRAP_CLAMP = 240;
    public static final int WRAP_REPEAT = 241;

    Image2D image;
    int blending = FUNC_MODULATE;
    int blendColor;
    int wrapS = WRAP_REPEAT, wrapT = WRAP_REPEAT;
    int levelFilter = FILTER_BASE_LEVEL, imageFilter = FILTER_NEAREST;

    public Texture2D(Image2D image) {
        setImage(image);
    }

    private Texture2D() {}

    Object3D dup() {
        Texture2D t = new Texture2D();
        copyTransformTo(t);
        t.image = image;
        t.blending = blending;
        t.blendColor = blendColor;
        t.wrapS = wrapS;
        t.wrapT = wrapT;
        t.levelFilter = levelFilter;
        t.imageFilter = imageFilter;
        return t;
    }

    int refs(Object3D[] out, int pos) { return add(out, super.refs(out, pos), image); }

    public void setImage(Image2D image) {
        if (image == null) throw new NullPointerException();
        this.image = image;
    }

    public Image2D getImage() { return image; }

    public void setFiltering(int levelFilter, int imageFilter) {
        if (levelFilter < FILTER_BASE_LEVEL || levelFilter > FILTER_NEAREST || imageFilter < FILTER_LINEAR || imageFilter > FILTER_NEAREST)
            throw new IllegalArgumentException();
        this.levelFilter = levelFilter;
        this.imageFilter = imageFilter;
    }

    public int getLevelFilter() { return levelFilter; }

    public int getImageFilter() { return imageFilter; }

    public void setWrapping(int wrapS, int wrapT) {
        if ((wrapS != WRAP_CLAMP && wrapS != WRAP_REPEAT) || (wrapT != WRAP_CLAMP && wrapT != WRAP_REPEAT)) throw new IllegalArgumentException();
        this.wrapS = wrapS;
        this.wrapT = wrapT;
    }

    public int getWrappingS() { return wrapS; }

    public int getWrappingT() { return wrapT; }

    public void setBlending(int func) {
        if (func < FUNC_ADD || func > FUNC_REPLACE) throw new IllegalArgumentException();
        blending = func;
    }

    public int getBlending() { return blending; }

    public void setBlendColor(int rgb) { blendColor = rgb & 0xFFFFFF; }

    public int getBlendColor() { return blendColor; }

    boolean acceptsProperty(int p) { return p == AnimationTrack.COLOR || super.acceptsProperty(p); }

    void applyAnimation(int property, float[] v, int comps) {
        if (property == AnimationTrack.COLOR) blendColor = colorFrom(v, 3, 0) & 0xFFFFFF;
        else super.applyAnimation(property, v, comps);
    }
}
