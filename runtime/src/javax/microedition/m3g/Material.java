package javax.microedition.m3g;

public class Material extends Object3D {
    public static final int AMBIENT = 1024;
    public static final int DIFFUSE = 2048;
    public static final int EMISSIVE = 4096;
    public static final int SPECULAR = 8192;

    int ambient = 0x333333, diffuse = 0xFFCCCCCC, emissive = 0, specular = 0;
    float shininess;
    boolean tracking;

    public Material() {}

    Object3D dup() {
        Material m = new Material();
        m.ambient = ambient;
        m.diffuse = diffuse;
        m.emissive = emissive;
        m.specular = specular;
        m.shininess = shininess;
        m.tracking = tracking;
        return m;
    }

    public void setColor(int target, int argb) {
        if (target == 0 || (target & ~(AMBIENT | DIFFUSE | EMISSIVE | SPECULAR)) != 0) throw new IllegalArgumentException();
        if ((target & AMBIENT) != 0) ambient = argb & 0xFFFFFF;
        if ((target & DIFFUSE) != 0) diffuse = argb;
        if ((target & EMISSIVE) != 0) emissive = argb & 0xFFFFFF;
        if ((target & SPECULAR) != 0) specular = argb & 0xFFFFFF;
    }

    public int getColor(int target) {
        switch (target) {
            case AMBIENT: return ambient;
            case DIFFUSE: return diffuse;
            case EMISSIVE: return emissive;
            case SPECULAR: return specular;
            default: throw new IllegalArgumentException();
        }
    }

    public void setShininess(float shininess) {
        if (shininess < 0 || shininess > 128) throw new IllegalArgumentException();
        this.shininess = shininess;
    }

    public float getShininess() { return shininess; }

    public void setVertexColorTrackingEnable(boolean enable) { tracking = enable; }

    public boolean isVertexColorTrackingEnabled() { return tracking; }

    boolean acceptsProperty(int p) {
        return p == AnimationTrack.ALPHA || p == AnimationTrack.AMBIENT_COLOR || p == AnimationTrack.DIFFUSE_COLOR
            || p == AnimationTrack.EMISSIVE_COLOR || p == AnimationTrack.SHININESS || p == AnimationTrack.SPECULAR_COLOR
            || super.acceptsProperty(p);
    }

    void applyAnimation(int property, float[] v, int comps) {
        switch (property) {
            case AnimationTrack.ALPHA: diffuse = (diffuse & 0xFFFFFF) | (clamp255(v[0]) << 24); break;
            case AnimationTrack.AMBIENT_COLOR: ambient = colorFrom(v, 3, 0) & 0xFFFFFF; break;
            case AnimationTrack.DIFFUSE_COLOR: diffuse = colorFrom(v, 3, diffuse); break;
            case AnimationTrack.EMISSIVE_COLOR: emissive = colorFrom(v, 3, 0) & 0xFFFFFF; break;
            case AnimationTrack.SPECULAR_COLOR: specular = colorFrom(v, 3, 0) & 0xFFFFFF; break;
            case AnimationTrack.SHININESS: shininess = Math.max(0, Math.min(128, v[0])); break;
            default: super.applyAnimation(property, v, comps);
        }
    }
}
