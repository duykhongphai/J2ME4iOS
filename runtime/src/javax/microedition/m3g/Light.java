package javax.microedition.m3g;

public class Light extends Node {
    public static final int AMBIENT = 128;
    public static final int DIRECTIONAL = 129;
    public static final int OMNI = 130;
    public static final int SPOT = 131;

    int mode = DIRECTIONAL;
    int color = 0xFFFFFF;
    float intensity = 1;
    float attConst = 1, attLinear, attQuad;
    float spotAngle = 45, spotExponent;

    public Light() {}

    Object3D dup() {
        Light l = new Light();
        copyNodeTo(l);
        l.mode = mode;
        l.color = color;
        l.intensity = intensity;
        l.attConst = attConst;
        l.attLinear = attLinear;
        l.attQuad = attQuad;
        l.spotAngle = spotAngle;
        l.spotExponent = spotExponent;
        return l;
    }

    public void setMode(int mode) {
        if (mode < AMBIENT || mode > SPOT) throw new IllegalArgumentException();
        this.mode = mode;
    }

    public int getMode() { return mode; }

    public void setIntensity(float intensity) { this.intensity = intensity; }

    public float getIntensity() { return intensity; }

    public void setColor(int rgb) { color = rgb & 0xFFFFFF; }

    public int getColor() { return color; }

    public void setSpotAngle(float angle) {
        if (angle < 0 || angle > 90) throw new IllegalArgumentException();
        spotAngle = angle;
    }

    public float getSpotAngle() { return spotAngle; }

    public void setSpotExponent(float exponent) {
        if (exponent < 0 || exponent > 128) throw new IllegalArgumentException();
        spotExponent = exponent;
    }

    public float getSpotExponent() { return spotExponent; }

    public void setAttenuation(float constant, float linear, float quadratic) {
        if (constant < 0 || linear < 0 || quadratic < 0 || constant + linear + quadratic == 0) throw new IllegalArgumentException();
        attConst = constant;
        attLinear = linear;
        attQuad = quadratic;
    }

    public float getConstantAttenuation() { return attConst; }

    public float getLinearAttenuation() { return attLinear; }

    public float getQuadraticAttenuation() { return attQuad; }

    /** Packs this light (already transformed by eyeFromLight) into 16 floats. */
    void pack(float[] eyeFromLight, float[] out, int off) {
        out[off] = mode;
        out[off + 1] = ((color >> 16) & 255) / 255f * intensity;
        out[off + 2] = ((color >> 8) & 255) / 255f * intensity;
        out[off + 3] = (color & 255) / 255f * intensity;
        float[] m = eyeFromLight;
        out[off + 4] = m[3];
        out[off + 5] = m[7];
        out[off + 6] = m[11];
        float dx = -m[2], dy = -m[6], dz = -m[10];
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l == 0) l = 1;
        out[off + 7] = dx / l;
        out[off + 8] = dy / l;
        out[off + 9] = dz / l;
        out[off + 10] = attConst;
        out[off + 11] = attLinear;
        out[off + 12] = attQuad;
        out[off + 13] = spotAngle;
        out[off + 14] = spotExponent;
        out[off + 15] = 0;
    }

    boolean acceptsProperty(int p) {
        return p == AnimationTrack.COLOR || p == AnimationTrack.INTENSITY || p == AnimationTrack.SPOT_ANGLE
            || p == AnimationTrack.SPOT_EXPONENT || super.acceptsProperty(p);
    }

    void applyAnimation(int property, float[] v, int comps) {
        switch (property) {
            case AnimationTrack.COLOR:
                color = colorFrom(v, 3, 0) & 0xFFFFFF;
                break;
            case AnimationTrack.INTENSITY:
                intensity = v[0];
                break;
            case AnimationTrack.SPOT_ANGLE:
                spotAngle = Math.max(0, Math.min(90, v[0]));
                break;
            case AnimationTrack.SPOT_EXPONENT:
                spotExponent = Math.max(0, Math.min(128, v[0]));
                break;
            default:
                super.applyAnimation(property, v, comps);
        }
    }
}
