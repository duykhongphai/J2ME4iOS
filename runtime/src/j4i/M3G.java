package j4i;

/** Native software 3D rasterizer used by javax.microedition.m3g. See core/m3g.c for the state layouts. */
public final class M3G {
    private M3G() {}

    /* integer state indices */
    public static final int I_CLIP_X0 = 0, I_CLIP_Y0 = 1, I_CLIP_X1 = 2, I_CLIP_Y1 = 3, I_VP_X = 4, I_VP_Y = 5, I_VP_W = 6, I_VP_H = 7,
        I_VCOUNT = 8, I_COL_COMPS = 9, I_DEFAULT_COLOR = 10, I_HAS_MATERIAL = 11, I_MAT_AMBIENT = 12, I_MAT_DIFFUSE = 13,
        I_MAT_EMISSIVE = 14, I_MAT_SPECULAR = 15, I_MAT_TRACKING = 16, I_CULLING = 17, I_WINDING = 18, I_SHADING = 19,
        I_TWO_SIDED = 20, I_LOCAL_CAMERA = 21, I_BLENDING = 22, I_DEPTH_TEST = 23, I_DEPTH_WRITE = 24, I_COLOR_WRITE = 25,
        I_ALPHA_WRITE = 26, I_FOG_MODE = 27, I_FOG_COLOR = 28, I_NUM_LIGHTS = 29, I_OPAQUE_TARGET = 30, I_TEX0 = 31, I_TEX1 = 39,
        I_TC0_COMPS = 47, I_TC1_COMPS = 48, I_COUNT = 49;

    /* float state indices */
    public static final int F_MV = 0, F_PROJ = 16, F_POS_SCALE = 32, F_POS_BIAS = 33, F_NRM_SCALE = 36, F_ALPHA_THRESHOLD = 37,
        F_DEPTH_FACTOR = 38, F_DEPTH_UNITS = 39, F_DEPTH_NEAR = 40, F_DEPTH_FAR = 41, F_SHININESS = 42, F_FOG_NEAR = 43,
        F_FOG_FAR = 44, F_FOG_DENSITY = 45, F_ALPHA_FACTOR = 46, F_TC0 = 48, F_TC1 = 68, F_LIGHTS = 88, F_COUNT = 88 + 16 * 8;

    public static native void drawTriangles(int[] target, float[] depth, int tw, int th, int[] ist, float[] fst, short[] pos,
                                            float[] posF, short[] nrm, float[] nrmF, short[] col, short[] tc0, int[] tex0,
                                            short[] tc1, int[] tex1, int[] idx, int nidx);

    public static native void clear(int[] target, float[] depth, int tw, int th, int[] ist, int argb, boolean clearColor, boolean clearDepth,
                                    float depthValue, int[] img, int iw, int ih, int imgFormat, int cropX, int cropY, int cropW,
                                    int cropH, int modeX, int modeY, boolean opaque);
}
