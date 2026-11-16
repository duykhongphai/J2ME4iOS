package com.mascotcapsule.micro3d.v3;

import j4i.M3G;
import java.util.Vector;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.J4IBridge;

/**
 * Mascot Capsule v3 renderer on top of the VM's software rasterizer (j4i.M3G).
 * Screen Y points down; the camera looks down +Z.
 */
public class Graphics3D {
    public static final int COMMAND_AFFINE_INDEX = 0x87000000;
    public static final int COMMAND_AMBIENT_LIGHT = 0xA0000000;
    public static final int COMMAND_ATTRIBUTE = 0x83000000;
    public static final int COMMAND_CENTER = 0x85000000;
    public static final int COMMAND_CLIP = 0x84000000;
    public static final int COMMAND_DIRECTION_LIGHT = 0xA1000000;
    public static final int COMMAND_END = 0x80000000;
    public static final int COMMAND_FLUSH = 0x82000000;
    public static final int COMMAND_LIST_VERSION_1_0 = 0xFE000001;
    public static final int COMMAND_NOP = 0x81000000;
    public static final int COMMAND_PARALLEL_SCALE = 0x90000000;
    public static final int COMMAND_PARALLEL_SIZE = 0x91000000;
    public static final int COMMAND_PERSPECTIVE_FOV = 0x92000000;
    public static final int COMMAND_PERSPECTIVE_WH = 0x93000000;
    public static final int COMMAND_TEXTURE_INDEX = 0x86000000;
    public static final int COMMAND_THRESHOLD = 0xAF000000;
    public static final int ENV_ATTR_LIGHTING = 1;
    public static final int ENV_ATTR_SPHERE_MAP = 2;
    public static final int ENV_ATTR_TOON_SHADING = 4;
    public static final int ENV_ATTR_SEMI_TRANSPARENT = 8;
    public static final int PATTR_LIGHTING = 1;
    public static final int PATTR_SPHERE_MAP = 2;
    public static final int PATTR_COLORKEY = 16;
    public static final int PATTR_BLEND_NORMAL = 0;
    public static final int PATTR_BLEND_HALF = 32;
    public static final int PATTR_BLEND_ADD = 64;
    public static final int PATTR_BLEND_SUB = 96;
    public static final int PDATA_NORMAL_NONE = 0;
    public static final int PDATA_NORMAL_PER_FACE = 512;
    public static final int PDATA_NORMAL_PER_VERTEX = 768;
    public static final int PDATA_COLOR_NONE = 0;
    public static final int PDATA_COLOR_PER_COMMAND = 1024;
    public static final int PDATA_COLOR_PER_FACE = 2048;
    public static final int PDATA_TEXURE_COORD_NONE = 0;
    public static final int PDATA_TEXURE_COORD = 12288;
    public static final int PDATA_POINT_SPRITE_PARAMS_PER_CMD = 4096;
    public static final int PDATA_POINT_SPRITE_PARAMS_PER_FACE = 8192;
    public static final int PDATA_POINT_SPRITE_PARAMS_PER_VERTEX = 12288;
    public static final int POINT_SPRITE_LOCAL_SIZE = 0;
    public static final int POINT_SPRITE_PIXEL_SIZE = 1;
    public static final int POINT_SPRITE_PERSPECTIVE = 0;
    public static final int POINT_SPRITE_NO_PERS = 2;
    public static final int PRIMITVE_POINTS = 0x1000000;
    public static final int PRIMITVE_LINES = 0x2000000;
    public static final int PRIMITVE_TRIANGLES = 0x3000000;
    public static final int PRIMITVE_QUADS = 0x4000000;
    public static final int PRIMITVE_POINT_SPRITES = 0x5000000;

    private static final int BLEND_HALF = 200, BLEND_ADD = 201, BLEND_SUB = 202;

    private Graphics bound;
    private int[] pixels;
    private int tw, th, clipX0, clipY0, clipX1, clipY1, originX, originY;
    private float[] depth;
    private final int[] ist = new int[M3G.I_COUNT];
    private final float[] fst = new float[M3G.F_COUNT];
    private final Vector deferred = new Vector(); /* semi-transparent draws, run on flush */

    /** Projection/lighting state for one draw. */
    static final class View {
        float[] mv = new float[16];
        float[] proj = new float[16];
        boolean lighting, toon, semi;
        float amb, dirI, lx, ly, lz;
        float toonThr, toonHigh, toonLow;
        float fx, fy, kx, ky; /* for sprites */
        boolean perspective;
    }

    /** A recorded triangle batch (drawn later for semi-transparent passes). */
    static final class Batch {
        View view;
        float[] pos;
        short[] col, tc;
        int count, blend, cull, texW, texH;
        int[] tex;
        boolean colorKey, depthWrite;
    }

    public Graphics3D() {}

    public final synchronized void bind(Graphics g) {
        if (g == null) throw new NullPointerException();
        if (bound != null) throw new IllegalStateException("Already bound");
        bound = g;
        pixels = J4IBridge.targetPixels(g);
        tw = J4IBridge.targetWidth(g);
        th = J4IBridge.targetHeight(g);
        int[] ct = new int[6];
        J4IBridge.clipAndTranslate(g, ct);
        clipX0 = ct[0];
        clipY0 = ct[1];
        clipX1 = ct[2];
        clipY1 = ct[3];
        originX = ct[4];
        originY = ct[5];
        if (depth == null || depth.length < tw * th) depth = new float[tw * th];
        clearDepth();
    }

    public final synchronized void release(Graphics g) {
        if (bound == null) return;
        if (g == bound) flush();
        else deferred.removeAllElements();
        bound = null;
        pixels = null;
    }

    public final void dispose() {}

    private void checkBound() {
        if (bound == null) throw new IllegalStateException("Graphics3D is not bound");
    }

    private void clearDepth() {
        baseState();
        M3G.clear(pixels, depth, tw, th, ist, 0, false, true, 1f, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, true);
    }

    private void baseState() {
        ist[M3G.I_CLIP_X0] = clipX0;
        ist[M3G.I_CLIP_Y0] = clipY0;
        ist[M3G.I_CLIP_X1] = clipX1;
        ist[M3G.I_CLIP_Y1] = clipY1;
        ist[M3G.I_VP_X] = 0;
        ist[M3G.I_VP_Y] = 0;
        ist[M3G.I_VP_W] = tw;
        ist[M3G.I_VP_H] = th;
        ist[M3G.I_OPAQUE_TARGET] = 1;
        fst[M3G.F_DEPTH_NEAR] = 0;
        fst[M3G.F_DEPTH_FAR] = 1;
    }

    public final void flush() {
        checkBound();
        for (int i = 0; i < deferred.size(); i++) draw((Batch) deferred.elementAt(i));
        deferred.removeAllElements();
        clearDepth();
    }

    /* ---------------- view setup ---------------- */
    private View makeView(FigureLayout l, Effect3D e, int x, int y, int envOverride) {
        View v = new View();
        AffineTrans a = l.affine();
        float[] m = v.mv;
        if (a == null) {
            m[0] = m[5] = m[10] = 1;
        } else {
            m[0] = a.m00 / 4096f;
            m[1] = a.m01 / 4096f;
            m[2] = a.m02 / 4096f;
            m[3] = a.m03;
            m[4] = a.m10 / 4096f;
            m[5] = a.m11 / 4096f;
            m[6] = a.m12 / 4096f;
            m[7] = a.m13;
            m[8] = a.m20 / 4096f;
            m[9] = a.m21 / 4096f;
            m[10] = a.m22 / 4096f;
            m[11] = a.m23;
        }
        m[15] = 1;
        float cx = originX + l.centerX + x, cy = originY + l.centerY + y;
        projection(v, l.mode, l, cx, cy);
        Light light = e.light;
        v.lighting = light != null;
        v.toon = e.shading == Effect3D.TOON_SHADING;
        v.semi = e.transparency;
        if (envOverride >= 0) {
            v.lighting = light != null && (envOverride & ENV_ATTR_LIGHTING) != 0;
            v.toon = (envOverride & ENV_ATTR_TOON_SHADING) != 0;
            v.semi = (envOverride & ENV_ATTR_SEMI_TRANSPARENT) != 0;
        }
        if (light != null) setLight(v, light.getAmbientIntensity(), light.getParallelLightIntensity(), light.getParallelLightDirection());
        v.toonThr = e.toonThreshold / 255f;
        v.toonHigh = e.toonHigh / 255f;
        v.toonLow = e.toonLow / 255f;
        return v;
    }

    private static void setLight(View v, int amb, int dir, Vector3D d) {
        v.amb = Math.max(0, Math.min(4096, amb)) / 4096f;
        v.dirI = Math.max(0, Math.min(16384, dir)) / 4096f;
        float len = (float) Math.sqrt((float) d.x * d.x + (float) d.y * d.y + (float) d.z * d.z);
        if (len == 0) len = 1;
        v.lx = d.x / len;
        v.ly = d.y / len;
        v.lz = d.z / len;
    }

    private void projection(View v, int mode, FigureLayout l, float cx, float cy) {
        float[] p = v.proj;
        for (int i = 0; i < 16; i++) p[i] = 0;
        float vw = tw, vh = th;
        if (mode == FigureLayout.PERSPECTIVE_FOV || mode == FigureLayout.PERSPECTIVE_WH) {
            float n = l.near, f = l.far;
            float fx, fy;
            if (mode == FigureLayout.PERSPECTIVE_FOV) {
                int ang = Math.max(2, Math.min(2046, l.angle));
                fx = fy = (float) ((vw / 2) / Math.tan(ang * Math.PI / 4096));
            } else {
                float W = l.perspW / 4096f, H = l.perspH / 4096f;
                fx = W > 0 ? n * vw / W : 1;
                fy = H > 0 ? n * vh / H : 1;
            }
            v.fx = fx;
            v.fy = fy;
            v.perspective = true;
            p[0] = 2 * fx / vw;
            p[2] = 2 * cx / vw - 1;
            p[5] = -2 * fy / vh;
            p[6] = 1 - 2 * cy / vh;
            p[10] = (f + n) / (f - n);
            p[11] = -2 * f * n / (f - n);
            p[14] = 1;
        } else {
            float kx, ky;
            if (mode == FigureLayout.PARALLEL_SIZE && l.parallelW > 0 && l.parallelH > 0) {
                kx = vw / l.parallelW;
                ky = vh / l.parallelH;
            } else {
                kx = l.scaleX / 4096f;
                ky = l.scaleY / 4096f;
            }
            v.kx = kx;
            v.ky = ky;
            p[0] = 2 * kx / vw;
            p[3] = 2 * cx / vw - 1;
            p[5] = -2 * ky / vh;
            p[7] = 1 - 2 * cy / vh;
            p[10] = 1f / 65536f;
            p[15] = 1;
        }
    }

    /** Camera-space light factor for a model-space normal (1 when unlit). */
    private static float shade(View v, float nx, float ny, float nz) {
        float[] m = v.mv;
        float cx = m[0] * nx + m[1] * ny + m[2] * nz, cy = m[4] * nx + m[5] * ny + m[6] * nz, cz = m[8] * nx + m[9] * ny + m[10] * nz;
        float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        float lam = 0;
        if (len > 0) lam = Math.max(0, (cx * v.lx + cy * v.ly + cz * v.lz) / len);
        float light = Math.min(v.amb + v.dirI * lam, 1f);
        if (v.toon) light = light < v.toonThr ? v.toonLow : v.toonHigh;
        return light;
    }

    /* ---------------- figures ---------------- */
    public final void renderFigure(Figure f, int x, int y, FigureLayout l, Effect3D e) {
        checkBound();
        if (f == null || l == null || e == null) throw new NullPointerException();
        drawFigureImpl(f, makeView(l, e, x, y, -1), f.textures, false);
    }

    public final void drawFigure(Figure f, int x, int y, FigureLayout l, Effect3D e) {
        checkBound();
        if (f == null || l == null || e == null) throw new NullPointerException();
        Texture[] tex = f.textures;
        Texture sel = f.getTexture();
        if (sel != null) tex = new Texture[] {sel};
        for (int i = 0; i < deferred.size(); i++) draw((Batch) deferred.elementAt(i));
        deferred.removeAllElements();
        drawFigureImpl(f, makeView(l, e, x, y, -1), tex, true);
    }

    private void drawFigureImpl(Figure f, View v, Texture[] textures, boolean immediate) {
        int nv = f.numVertices;
        float[] light = new float[nv];
        boolean lit = v.lighting && f.normals != null;
        for (int i = 0; i < nv; i++) light[i] = lit ? shade(v, f.normals[i * 3], f.normals[i * 3 + 1], f.normals[i * 3 + 2]) : 1f;
        /* group triangles by state */
        Vector batches = new Vector();
        java.util.Hashtable byKey = new java.util.Hashtable();
        for (int t = 0; t < f.numTex; t++) {
            if (!f.visible(f.tPat[t])) continue;
            int ti = f.tTex[t];
            Texture tex = textures != null && textures.length > 0 ? textures[textures.length == 1 ? 0 : Math.min(ti, textures.length - 1)] : null;
            if (tex == null || tex.argb == null) continue;
            int mat = f.tMat[t];
            Integer key = new Integer((ti << 12) | (mat & 0x7F) | 0x100);
            Batch b = batch(byKey, batches, key, v, mat, tex, f.numTex);
            boolean plit = lit && (mat & Figure.M_LIGHTING) != 0;
            for (int k = 0; k < 3; k++) {
                int vi = f.tIdx[t * 3 + k];
                float l = plit ? light[vi] : 1f;
                put(b, f.verts, vi, l, l, l);
                b.tc[b.count * 2 - 2] = (short) f.tUV[t * 6 + k * 2];
                b.tc[b.count * 2 - 1] = (short) f.tUV[t * 6 + k * 2 + 1];
            }
        }
        for (int t = 0; t < f.numCol; t++) {
            if (!f.visible(f.cPat[t])) continue;
            int mat = f.cMat[t];
            Integer key = new Integer((mat & 0x7E) | 0x200);
            Batch b = batch(byKey, batches, key, v, mat, null, f.numCol);
            boolean plit = lit && (mat & Figure.M_LIGHTING) != 0;
            int c = f.cColor[t];
            float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, bl = (c & 255) / 255f;
            for (int k = 0; k < 3; k++) {
                int vi = f.cIdx[t * 3 + k];
                float l = plit ? light[vi] : 1f;
                put(b, f.verts, vi, r * l, g * l, bl * l);
            }
        }
        for (int i = 0; i < batches.size(); i++) {
            Batch b = (Batch) batches.elementAt(i);
            if (b.count == 0) continue;
            if (b.blend != 0 && !immediate) deferred.addElement(b);
            else draw(b);
        }
    }

    private Batch batch(java.util.Hashtable byKey, Vector list, Integer key, View v, int mat, Texture tex, int maxTris) {
        Batch b = (Batch) byKey.get(key);
        if (b != null) return b;
        b = new Batch();
        b.view = v;
        int blend = v.semi ? (mat & Figure.M_BLEND_MASK) : 0;
        b.blend = blend == 2 ? BLEND_HALF : blend == 4 ? BLEND_ADD : blend == 6 ? BLEND_SUB : 0;
        b.cull = (mat & Figure.M_DOUBLE_FACE) != 0 ? 162 : 160;
        b.colorKey = tex != null && (mat & Figure.M_TRANSPARENT) != 0;
        b.depthWrite = b.blend == 0;
        if (tex != null) {
            b.tex = tex.argb;
            b.texW = tex.width;
            b.texH = tex.height;
            b.tc = new short[maxTris * 6];
        }
        b.pos = new float[maxTris * 9];
        b.col = new short[maxTris * 9];
        byKey.put(key, b);
        list.addElement(b);
        return b;
    }

    private static void put(Batch b, float[] verts, int vi, float r, float g, float bl) {
        int o = b.count * 3;
        if (o + 3 > b.pos.length) grow(b);
        b.pos[o] = verts[vi * 3];
        b.pos[o + 1] = verts[vi * 3 + 1];
        b.pos[o + 2] = verts[vi * 3 + 2];
        b.col[o] = (short) Math.min(255, (int) (r * 255 + 0.5f));
        b.col[o + 1] = (short) Math.min(255, (int) (g * 255 + 0.5f));
        b.col[o + 2] = (short) Math.min(255, (int) (bl * 255 + 0.5f));
        b.count++;
    }

    private static void grow(Batch b) {
        int n = Math.max(b.pos.length * 2, 36);
        float[] p = new float[n];
        System.arraycopy(b.pos, 0, p, 0, b.pos.length);
        b.pos = p;
        short[] c = new short[n];
        System.arraycopy(b.col, 0, c, 0, b.col.length);
        b.col = c;
        if (b.tc != null) {
            short[] t = new short[n / 3 * 2];
            System.arraycopy(b.tc, 0, t, 0, b.tc.length);
            b.tc = t;
        }
    }

    private void draw(Batch b) {
        if (pixels == null || b.count < 3) return;
        baseState();
        View v = b.view;
        System.arraycopy(v.mv, 0, fst, M3G.F_MV, 16);
        System.arraycopy(v.proj, 0, fst, M3G.F_PROJ, 16);
        ist[M3G.I_VCOUNT] = b.count;
        ist[M3G.I_COL_COMPS] = 3;
        ist[M3G.I_DEFAULT_COLOR] = 0xFFFFFFFF;
        ist[M3G.I_HAS_MATERIAL] = 0;
        ist[M3G.I_CULLING] = b.cull;
        ist[M3G.I_WINDING] = 169; /* screen Y points down: front faces are clockwise in our NDC */
        ist[M3G.I_SHADING] = 165;
        ist[M3G.I_TWO_SIDED] = 0;
        ist[M3G.I_BLENDING] = b.blend != 0 ? b.blend : 68;
        ist[M3G.I_DEPTH_TEST] = 1;
        ist[M3G.I_DEPTH_WRITE] = b.depthWrite ? 1 : 0;
        ist[M3G.I_COLOR_WRITE] = 1;
        ist[M3G.I_ALPHA_WRITE] = 0;
        ist[M3G.I_FOG_MODE] = 0;
        ist[M3G.I_NUM_LIGHTS] = 0;
        fst[M3G.F_ALPHA_THRESHOLD] = b.colorKey ? 0.5f : 0f;
        fst[M3G.F_DEPTH_FACTOR] = 0;
        fst[M3G.F_DEPTH_UNITS] = 0;
        fst[M3G.F_ALPHA_FACTOR] = 1;
        fst[M3G.F_POS_SCALE] = 1;
        int[] tex = null;
        if (b.tex != null) {
            tex = b.tex;
            ist[M3G.I_TEX0] = b.texW;
            ist[M3G.I_TEX0 + 1] = b.texH;
            ist[M3G.I_TEX0 + 2] = 3;
            ist[M3G.I_TEX0 + 3] = 240;
            ist[M3G.I_TEX0 + 4] = 240;
            ist[M3G.I_TEX0 + 5] = 210;
            ist[M3G.I_TEX0 + 6] = 227; /* modulate */
            ist[M3G.I_TEX0 + 7] = 0;
            ist[M3G.I_TC0_COMPS] = 2;
            fst[M3G.F_TC0] = 1;
            fst[M3G.F_TC0 + 1] = fst[M3G.F_TC0 + 2] = fst[M3G.F_TC0 + 3] = 0;
            for (int i = 0; i < 16; i++) fst[M3G.F_TC0 + 4 + i] = 0;
            fst[M3G.F_TC0 + 4] = 1f / b.texW;
            fst[M3G.F_TC0 + 4 + 5] = 1f / b.texH;
            fst[M3G.F_TC0 + 4 + 10] = 1;
            fst[M3G.F_TC0 + 4 + 15] = 1;
        }
        int[] idx = sequence(b.count);
        M3G.drawTriangles(pixels, depth, tw, th, ist, fst, null, b.pos, null, null, b.col, tex != null ? b.tc : null, tex, null, null,
                          idx, b.count - b.count % 3);
    }

    private int[] seq = new int[0];

    private int[] sequence(int n) {
        if (seq.length < n) {
            seq = new int[Math.max(n, seq.length * 2)];
            for (int i = 0; i < seq.length; i++) seq[i] = i;
        }
        return seq;
    }

    /* ---------------- primitives ---------------- */
    public final void renderPrimitives(Texture t, int x, int y, FigureLayout l, Effect3D e, int command, int numPrimitives,
                                       int[] vertexCoords, int[] normals, int[] textureCoords, int[] colors) {
        checkBound();
        if (l == null || e == null || vertexCoords == null || normals == null || textureCoords == null || colors == null)
            throw new NullPointerException();
        if (numPrimitives < 1 || numPrimitives > 255 || command < 0) throw new IllegalArgumentException();
        View v = makeView(l, e, x, y, -1);
        primitives(v, t, command, numPrimitives, vertexCoords, 0, normals, 0, textureCoords, 0, colors, 0, false);
    }

    public final void drawCommandList(Texture texture, int x, int y, FigureLayout l, Effect3D e, int[] commandList) {
        drawCommandList(texture != null ? new Texture[] {texture} : null, x, y, l, e, commandList);
    }

    public final void drawCommandList(Texture[] textures, int x, int y, FigureLayout l, Effect3D e, int[] commandList) {
        checkBound();
        if (l == null || e == null || commandList == null) throw new NullPointerException();
        if (commandList.length == 0 || commandList[0] != COMMAND_LIST_VERSION_1_0) throw new IllegalArgumentException("Bad command list");
        /* work on a copy of the layout state */
        FigureLayout lay = new FigureLayout();
        lay.affineArray = l.affineArray;
        lay.affineIndex = l.affineIndex;
        lay.centerX = l.centerX;
        lay.centerY = l.centerY;
        lay.scaleX = l.scaleX;
        lay.scaleY = l.scaleY;
        lay.parallelW = l.parallelW;
        lay.parallelH = l.parallelH;
        lay.near = l.near;
        lay.far = l.far;
        lay.angle = l.angle;
        lay.perspW = l.perspW;
        lay.perspH = l.perspH;
        lay.mode = l.mode;
        Effect3D eff = new Effect3D();
        eff.light = e.light != null ? new Light(e.light.getParallelLightDirection(), e.light.getParallelLightIntensity(),
                                                e.light.getAmbientIntensity())
                                    : null;
        eff.shading = e.shading;
        eff.transparency = e.transparency;
        eff.toonThreshold = e.toonThreshold;
        eff.toonHigh = e.toonHigh;
        eff.toonLow = e.toonLow;
        int env = -1;
        Texture tex = textures != null && textures.length > 0 ? textures[0] : null;
        int[] c = commandList;
        int i = 1;
        int sx0 = clipX0, sy0 = clipY0, sx1 = clipX1, sy1 = clipY1;
        try {
            while (i < c.length) {
                int cmd = c[i++];
                int op = cmd & 0xFF000000;
                if (op == COMMAND_END) break;
                if (op == COMMAND_NOP) {
                    i += cmd & 0xFFFFFF;
                } else if (op == COMMAND_FLUSH) {
                    flush();
                } else if (op == COMMAND_ATTRIBUTE) {
                    env = cmd & 0xFFFFFF;
                } else if (op == COMMAND_AFFINE_INDEX) {
                    int idx = cmd & 0xFFFFFF;
                    if (lay.affineArray != null && idx < lay.affineArray.length) lay.affineIndex = idx;
                } else if (op == COMMAND_CENTER) {
                    lay.centerX = c[i++];
                    lay.centerY = c[i++];
                } else if (op == COMMAND_CLIP) {
                    int cl = c[i++] + originX, ct = c[i++] + originY, cr = c[i++] + originX, cb = c[i++] + originY;
                    clipX0 = Math.max(clipX0, cl);
                    clipY0 = Math.max(clipY0, ct);
                    clipX1 = Math.min(clipX1, cr);
                    clipY1 = Math.min(clipY1, cb);
                } else if (op == COMMAND_PARALLEL_SCALE) {
                    lay.scaleX = c[i++];
                    lay.scaleY = c[i++];
                    lay.mode = FigureLayout.PARALLEL_SCALE;
                } else if (op == COMMAND_PARALLEL_SIZE) {
                    lay.parallelW = c[i++];
                    lay.parallelH = c[i++];
                    lay.mode = FigureLayout.PARALLEL_SIZE;
                } else if (op == COMMAND_PERSPECTIVE_FOV) {
                    lay.near = c[i++];
                    lay.far = c[i++];
                    lay.angle = c[i++];
                    lay.mode = FigureLayout.PERSPECTIVE_FOV;
                } else if (op == COMMAND_PERSPECTIVE_WH) {
                    lay.near = c[i++];
                    lay.far = c[i++];
                    lay.perspW = c[i++];
                    lay.perspH = c[i++];
                    lay.mode = FigureLayout.PERSPECTIVE_WH;
                } else if (op == COMMAND_TEXTURE_INDEX) {
                    int ti = cmd & 0xFFFFFF;
                    if (textures != null && ti >= 0 && ti < textures.length) tex = textures[ti];
                } else if (op == COMMAND_THRESHOLD) {
                    eff.toonThreshold = c[i++];
                    eff.toonHigh = c[i++];
                    eff.toonLow = c[i++];
                } else if (op == COMMAND_AMBIENT_LIGHT) {
                    if (eff.light == null) eff.light = new Light(new Vector3D(0, 0, 4096), 0, 0);
                    eff.light.setAmbientIntensity(c[i++]);
                } else if (op == COMMAND_DIRECTION_LIGHT) {
                    if (eff.light == null) eff.light = new Light(new Vector3D(0, 0, 4096), 0, 0);
                    eff.light.setParallelLightDirection(new Vector3D(c[i], c[i + 1], c[i + 2]));
                    eff.light.setParallelLightIntensity(c[i + 3]);
                    i += 4;
                } else if ((cmd & 0x80000000) == 0 && (cmd & 0x7000000) != 0) {
                    int n = (cmd >> 16) & 0xFF;
                    View v = makeView(lay, eff, x, y, env);
                    i = primitives(v, tex, cmd, n, c, i, c, -1, c, -1, c, -1, true);
                } else {
                    break; /* unknown command */
                }
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            /* truncated list: stop */
        } finally {
            clipX0 = sx0;
            clipY0 = sy0;
            clipX1 = sx1;
            clipY1 = sy1;
        }
    }

    /**
     * Draws primitives. In a command list all data follows the command in `vtx` (inline = true):
     * offsets for normals/texcoords/colors are computed there. Returns the next read position.
     */
    private int primitives(View v, Texture tex, int cmd, int n, int[] vtx, int vo, int[] nrm, int no, int[] tcs, int to, int[] cols,
                           int co, boolean inline) {
        int type = cmd & 0x7000000;
        int nflag = cmd & 0x300, cflag = cmd & 0xC00, tflag = cmd & 0x3000;
        int sizeOf = type == PRIMITVE_POINTS ? 1 : type == PRIMITVE_LINES ? 2 : type == PRIMITVE_TRIANGLES ? 3 : type == PRIMITVE_QUADS ? 4 : 1;
        int p = vo;
        int vStart = p;
        p += n * 3 * sizeOf;
        int nStart = inline ? p : no;
        if (nflag == PDATA_NORMAL_PER_FACE) p += inline ? n * 3 : 0;
        else if (nflag == PDATA_NORMAL_PER_VERTEX) p += inline ? n * 3 * sizeOf : 0;
        int tStart = inline ? p : to;
        if (type == PRIMITVE_POINT_SPRITES) {
            if (tflag == PDATA_POINT_SPRITE_PARAMS_PER_CMD) p += inline ? 8 : 0;
            else if (tflag != 0) p += inline ? n * 8 : 0;
        } else if (tflag == PDATA_TEXURE_COORD) p += inline ? n * 2 * sizeOf : 0;
        int cStart = inline ? p : co;
        if (cflag == PDATA_COLOR_PER_COMMAND) p += inline ? 1 : 0;
        else if (cflag == PDATA_COLOR_PER_FACE) p += inline ? n : 0;
        else if (cflag != 0) p += inline ? n * sizeOf : 0;
        if (!inline) {
            nStart = 0;
            tStart = 0;
            cStart = 0;
        }
        if (type == PRIMITVE_TRIANGLES || type == PRIMITVE_QUADS) {
            boolean textured = tflag == PDATA_TEXURE_COORD;
            if (textured && (tex == null || tex.argb == null)) return p;
            if (!textured && cflag == 0) return p;
            int blendBits = (cmd & 96) >> 4;
            int mat = (v.semi ? blendBits : 0) | ((cmd & PATTR_COLORKEY) != 0 ? Figure.M_TRANSPARENT : 0) | Figure.M_DOUBLE_FACE;
            Batch b = new Batch();
            b.view = v;
            b.blend = blendBits == 2 ? BLEND_HALF : blendBits == 4 ? BLEND_ADD : blendBits == 6 ? BLEND_SUB : 0;
            if (!v.semi) b.blend = 0;
            b.cull = 162;
            b.colorKey = textured && (cmd & PATTR_COLORKEY) != 0;
            b.depthWrite = b.blend == 0;
            int tris = type == PRIMITVE_QUADS ? n * 2 : n;
            b.pos = new float[tris * 9];
            b.col = new short[tris * 9];
            if (textured) {
                b.tex = tex.argb;
                b.texW = tex.width;
                b.texH = tex.height;
                b.tc = new short[tris * 6];
            }
            boolean lit = v.lighting && (cmd & PATTR_LIGHTING) != 0 && nflag != 0;
            int[] order = type == PRIMITVE_QUADS ? new int[] {0, 1, 2, 3, 0, 2} : new int[] {0, 1, 2};
            float[] tmp = new float[sizeOf * 3];
            for (int k = 0; k < n; k++) {
                for (int j = 0; j < sizeOf * 3; j++) tmp[j] = vtx[vStart + k * sizeOf * 3 + j];
                int color = 0xFFFFFF;
                if (!textured) color = cflag == PDATA_COLOR_PER_COMMAND ? cols[cStart] : cols[cStart + k];
                for (int oi = 0; oi < order.length; oi++) {
                    int vi = order[oi];
                    float l = 1;
                    if (lit) {
                        int nb = nflag == PDATA_NORMAL_PER_FACE ? nStart + k * 3 : nStart + (k * sizeOf + vi) * 3;
                        l = shade(v, nrm[nb], nrm[nb + 1], nrm[nb + 2]);
                    }
                    float r = ((color >> 16) & 255) / 255f * l, g = ((color >> 8) & 255) / 255f * l, bl = (color & 255) / 255f * l;
                    put(b, tmp, vi, r, g, bl);
                    if (textured) {
                        int tb = tStart + (k * sizeOf + vi) * 2;
                        b.tc[b.count * 2 - 2] = (short) (tcs[tb] & 255);
                        b.tc[b.count * 2 - 1] = (short) (tcs[tb + 1] & 255);
                    }
                }
            }
            if (b.blend != 0) deferred.addElement(b);
            else draw(b);
        } else if (type == PRIMITVE_POINT_SPRITES) {
            if (tex == null || tex.argb == null || tflag == 0) return p;
            pointSprites(v, tex, cmd, n, vtx, vStart, tcs, tStart, tflag == PDATA_POINT_SPRITE_PARAMS_PER_CMD);
        }
        /* points and lines are not drawn */
        return p;
    }

    private void pointSprites(View v, Texture tex, int cmd, int n, int[] vtx, int vStart, int[] prm, int pStart, boolean perCmd) {
        Batch b = new Batch();
        View cam = new View();
        System.arraycopy(v.proj, 0, cam.proj, 0, 16);
        cam.mv[0] = cam.mv[5] = cam.mv[10] = cam.mv[15] = 1;
        b.view = cam;
        int blendBits = (cmd & 96) >> 4;
        b.blend = !v.semi ? 0 : blendBits == 2 ? BLEND_HALF : blendBits == 4 ? BLEND_ADD : blendBits == 6 ? BLEND_SUB : 0;
        b.cull = 162;
        b.colorKey = (cmd & PATTR_COLORKEY) != 0;
        b.depthWrite = b.blend == 0;
        b.tex = tex.argb;
        b.texW = tex.width;
        b.texH = tex.height;
        b.pos = new float[n * 18];
        b.col = new short[n * 18];
        b.tc = new short[n * 12];
        float[] m = v.mv;
        float[] q = new float[12];
        for (int k = 0; k < n; k++) {
            int po = perCmd ? pStart : pStart + k * 8;
            int w = prm[po], h = prm[po + 1], ang = prm[po + 2], tx0 = prm[po + 3], ty0 = prm[po + 4], tx1 = prm[po + 5] - 1,
                ty1 = prm[po + 6] - 1, flags = prm[po + 7];
            float x = vtx[vStart + k * 3], y = vtx[vStart + k * 3 + 1], z = vtx[vStart + k * 3 + 2];
            float ex = m[0] * x + m[1] * y + m[2] * z + m[3], ey = m[4] * x + m[5] * y + m[6] * z + m[7], ez = m[8] * x + m[9] * y + m[10] * z + m[11];
            float hx, hy;
            if ((flags & POINT_SPRITE_PIXEL_SIZE) != 0) {
                if (v.perspective) {
                    hx = w / 2f * ez / v.fx;
                    hy = h / 2f * ez / v.fy;
                } else {
                    hx = w / 2f / (v.kx == 0 ? 1 : v.kx);
                    hy = h / 2f / (v.ky == 0 ? 1 : v.ky);
                }
            } else {
                hx = w / 2f;
                hy = h / 2f;
            }
            float s = (float) Math.sin(ang * Math.PI / 2048), c = (float) Math.cos(ang * Math.PI / 2048);
            float[][] corners = {{-hx, -hy}, {hx, -hy}, {hx, hy}, {-hx, hy}};
            for (int j = 0; j < 4; j++) {
                float cx = corners[j][0] * c - corners[j][1] * s, cy = corners[j][0] * s + corners[j][1] * c;
                q[j * 3] = ex + cx;
                q[j * 3 + 1] = ey + cy;
                q[j * 3 + 2] = ez;
            }
            int[] uvs = {tx0, ty0, tx1, ty0, tx1, ty1, tx0, ty1};
            int[] order = {0, 1, 2, 0, 2, 3};
            for (int oi = 0; oi < 6; oi++) {
                int vi = order[oi];
                put(b, q, vi, 1, 1, 1);
                b.tc[b.count * 2 - 2] = (short) uvs[vi * 2];
                b.tc[b.count * 2 - 1] = (short) uvs[vi * 2 + 1];
            }
        }
        if (b.blend != 0) deferred.addElement(b);
        else draw(b);
    }
}
