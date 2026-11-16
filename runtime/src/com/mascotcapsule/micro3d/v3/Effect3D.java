package com.mascotcapsule.micro3d.v3;

public class Effect3D {
    public static final int NORMAL_SHADING = 0;
    public static final int TOON_SHADING = 1;

    Light light;
    int shading = NORMAL_SHADING;
    boolean transparency = true;
    Texture sphere;
    int toonThreshold = 128, toonHigh = 255, toonLow = 128;

    public Effect3D() {}

    public Effect3D(Light light, int shading, boolean isEnableTrans, Texture tex) {
        this.light = light;
        setShadingType(shading);
        transparency = isEnableTrans;
        setSphereTexture(tex);
    }

    public final Light getLight() { return light; }

    public final void setLight(Light light) { this.light = light; }

    public final int getShadingType() { return shading; }

    public final void setShadingType(int shading) {
        if (shading != NORMAL_SHADING && shading != TOON_SHADING) throw new IllegalArgumentException();
        this.shading = shading;
    }

    public final int getToonThreshold() { return toonThreshold; }

    public final int getToonHigh() { return toonHigh; }

    public final int getToonLow() { return toonLow; }

    public final void setToonParams(int threshold, int high, int low) {
        if (threshold < 0 || threshold > 255 || high < 0 || high > 255 || low < 0 || low > 255) throw new IllegalArgumentException();
        toonThreshold = threshold;
        toonHigh = high;
        toonLow = low;
    }

    public final boolean isTransparency() { return transparency; }

    public final void setTransparency(boolean isEnable) { transparency = isEnable; }

    public final Texture getSphereTexture() { return sphere; }

    public final void setSphereTexture(Texture tex) {
        if (tex != null && tex.forModel) throw new IllegalArgumentException("Texture is for a model");
        sphere = tex;
    }

    /* deprecated aliases */
    public final int getShading() { return shading; }

    public final void setShading(int shading) { setShadingType(shading); }

    public final Texture getSphereMap() { return sphere; }

    public final void setSphereMap(Texture tex) { setSphereTexture(tex); }

    public final int getThreshold() { return toonThreshold; }

    public final int getThresholdHigh() { return toonHigh; }

    public final int getThresholdLow() { return toonLow; }

    public final void setThreshold(int threshold, int high, int low) { setToonParams(threshold, high, low); }

    public final boolean isSemiTransparentEnabled() { return transparency; }

    public final void setSemiTransparentEnabled(boolean isEnable) { transparency = isEnable; }
}
