package javax.microedition.m3g;

public class CompositingMode extends Object3D {
    public static final int ALPHA = 64;
    public static final int ALPHA_ADD = 65;
    public static final int MODULATE = 66;
    public static final int MODULATE_X2 = 67;
    public static final int REPLACE = 68;

    int blending = REPLACE;
    float alphaThreshold;
    boolean depthTest = true, depthWrite = true, colorWrite = true, alphaWrite = true;
    float offsetFactor, offsetUnits;

    public CompositingMode() {}

    Object3D dup() {
        CompositingMode c = new CompositingMode();
        c.blending = blending;
        c.alphaThreshold = alphaThreshold;
        c.depthTest = depthTest;
        c.depthWrite = depthWrite;
        c.colorWrite = colorWrite;
        c.alphaWrite = alphaWrite;
        c.offsetFactor = offsetFactor;
        c.offsetUnits = offsetUnits;
        return c;
    }

    public void setBlending(int mode) {
        if (mode < ALPHA || mode > REPLACE) throw new IllegalArgumentException();
        blending = mode;
    }

    public int getBlending() { return blending; }

    public void setAlphaThreshold(float threshold) {
        if (threshold < 0 || threshold > 1) throw new IllegalArgumentException();
        alphaThreshold = threshold;
    }

    public float getAlphaThreshold() { return alphaThreshold; }

    public void setAlphaWriteEnable(boolean enable) { alphaWrite = enable; }

    public boolean isAlphaWriteEnabled() { return alphaWrite; }

    public void setColorWriteEnable(boolean enable) { colorWrite = enable; }

    public boolean isColorWriteEnabled() { return colorWrite; }

    public void setDepthWriteEnable(boolean enable) { depthWrite = enable; }

    public boolean isDepthWriteEnabled() { return depthWrite; }

    public void setDepthTestEnable(boolean enable) { depthTest = enable; }

    public boolean isDepthTestEnabled() { return depthTest; }

    public void setDepthOffset(float factor, float units) {
        offsetFactor = factor;
        offsetUnits = units;
    }

    public float getDepthOffsetFactor() { return offsetFactor; }

    public float getDepthOffsetUnits() { return offsetUnits; }
}
