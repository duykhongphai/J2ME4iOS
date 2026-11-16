package javax.microedition.m3g;

import j4i.M3G;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.J4IBridge;

public class Graphics3D {
    public static final int ANTIALIAS = 2;
    public static final int DITHER = 4;
    public static final int TRUE_COLOR = 8;
    public static final int OVERWRITE = 16;

    private static Graphics3D instance;

    private Object target;
    private int[] pixels;
    private int tw, th;
    private boolean opaque;
    private float[] depth;
    private boolean depthEnabled = true;
    private int hints;
    private int clipX0, clipY0, clipX1, clipY1, originX, originY;
    private int vpX, vpY, vpW, vpH;
    private float depthNear = 0, depthFar = 1;

    private Camera camera;
    private final float[] cameraWorld = new float[16];
    private final float[] view = new float[16];
    private final Vector lights = new Vector();
    private final Vector lightTransforms = new Vector();

    private final int[] ist = new int[M3G.I_COUNT];
    private final float[] fst = new float[M3G.F_COUNT];

    private Graphics3D() {}

    public static synchronized Graphics3D getInstance() {
        if (instance == null) instance = new Graphics3D();
        return instance;
    }

    public static Hashtable getProperties() {
        Hashtable h = new Hashtable();
        h.put("supportAntialiasing", Boolean.FALSE);
        h.put("supportTrueColor", Boolean.TRUE);
        h.put("supportDithering", Boolean.FALSE);
        h.put("supportMipmapping", Boolean.FALSE);
        h.put("supportPerspectiveCorrection", Boolean.TRUE);
        h.put("supportLocalCameraLighting", Boolean.TRUE);
        h.put("maxLights", new Integer(8));
        h.put("maxViewportWidth", new Integer(2048));
        h.put("maxViewportHeight", new Integer(2048));
        h.put("maxViewportDimension", new Integer(2048));
        h.put("maxTextureDimension", new Integer(1024));
        h.put("maxSpriteCropDimension", new Integer(1024));
        h.put("maxTransformsPerVertex", new Integer(4));
        h.put("numTextureUnits", new Integer(2));
        return h;
    }

    /* ---------------- target ---------------- */
    public void bindTarget(Object target) { bindTarget(target, true, 0); }

    public synchronized void bindTarget(Object target, boolean depthBuffer, int hints) {
        if (target == null) throw new NullPointerException();
        if (this.target != null) throw new IllegalStateException("A target is already bound");
        if (target instanceof Graphics) {
            Graphics g = (Graphics) target;
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
            opaque = true;
        } else if (target instanceof Image2D) {
            Image2D img = (Image2D) target;
            if (!img.mutable || (img.format != Image2D.RGB && img.format != Image2D.RGBA)) throw new IllegalArgumentException();
            pixels = img.argb;
            tw = img.width;
            th = img.height;
            clipX0 = clipY0 = 0;
            clipX1 = tw;
            clipY1 = th;
            originX = originY = 0;
            opaque = img.format == Image2D.RGB;
        } else throw new IllegalArgumentException("Unsupported target");
        this.target = target;
        this.depthEnabled = depthBuffer;
        this.hints = hints;
        if (depth == null || depth.length < tw * th) depth = new float[tw * th];
        vpX = clipX0;
        vpY = clipY0;
        vpW = Math.max(1, clipX1 - clipX0);
        vpH = Math.max(1, clipY1 - clipY0);
    }

    public synchronized void releaseTarget() {
        target = null;
        pixels = null;
    }

    public Object getTarget() { return target; }

    public int getHints() { return hints; }

    public boolean isDepthBufferEnabled() { return depthEnabled; }

    public void setViewport(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0 || width > 2048 || height > 2048) throw new IllegalArgumentException();
        vpX = x + originX;
        vpY = y + originY;
        vpW = width;
        vpH = height;
    }

    public int getViewportX() { return vpX - originX; }

    public int getViewportY() { return vpY - originY; }

    public int getViewportWidth() { return vpW; }

    public int getViewportHeight() { return vpH; }

    public void setDepthRange(float near, float far) {
        if (near < 0 || near > 1 || far < 0 || far > 1) throw new IllegalArgumentException();
        depthNear = near;
        depthFar = far;
    }

    public float getDepthRangeNear() { return depthNear; }

    public float getDepthRangeFar() { return depthFar; }

    private void checkTarget() {
        if (target == null) throw new IllegalStateException("No target bound");
    }

    private void baseState() {
        ist[M3G.I_CLIP_X0] = clipX0;
        ist[M3G.I_CLIP_Y0] = clipY0;
        ist[M3G.I_CLIP_X1] = clipX1;
        ist[M3G.I_CLIP_Y1] = clipY1;
        ist[M3G.I_VP_X] = vpX;
        ist[M3G.I_VP_Y] = vpY;
        ist[M3G.I_VP_W] = vpW;
        ist[M3G.I_VP_H] = vpH;
        ist[M3G.I_OPAQUE_TARGET] = opaque ? 1 : 0;
        fst[M3G.F_DEPTH_NEAR] = depthNear;
        fst[M3G.F_DEPTH_FAR] = depthFar;
    }

    /* ---------------- clear ---------------- */
    public void clear(Background background) {
        checkTarget();
        baseState();
        if (background == null) {
            M3G.clear(pixels, depth, tw, th, ist, 0, true, depthEnabled, 1f, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, opaque);
            return;
        }
        Image2D img = background.image;
        M3G.clear(pixels, depth, tw, th, ist, background.color, background.colorClear, background.depthClear && depthEnabled, 1f,
                  img != null ? img.argb : null, img != null ? img.width : 0, img != null ? img.height : 0, img != null ? img.formatBits() : 0,
                  background.cropX, background.cropY, background.cropW, background.cropH, background.modeX, background.modeY, opaque);
    }

    /* ---------------- camera and lights ---------------- */
    public void setCamera(Camera camera, Transform transform) {
        this.camera = camera;
        if (transform != null) System.arraycopy(transform.m, 0, cameraWorld, 0, 16);
        else new Transform().get(cameraWorld);
        if (!Transform.invert(cameraWorld, view)) throw new ArithmeticException("Camera transform is not invertible");
    }

    public Camera getCamera(Transform transform) {
        if (transform != null) transform.set(cameraWorld);
        return camera;
    }

    public int addLight(Light light, Transform transform) {
        if (light == null) throw new NullPointerException();
        lights.addElement(light);
        lightTransforms.addElement(transform != null ? new Transform(transform) : new Transform());
        return lights.size() - 1;
    }

    public void setLight(int index, Light light, Transform transform) {
        if (index < 0 || index >= lights.size()) throw new IndexOutOfBoundsException();
        lights.setElementAt(light, index);
        lightTransforms.setElementAt(transform != null ? new Transform(transform) : new Transform(), index);
    }

    public void resetLights() {
        lights.removeAllElements();
        lightTransforms.removeAllElements();
    }

    public int getLightCount() { return lights.size(); }

    public Light getLight(int index, Transform transform) {
        if (index < 0 || index >= lights.size()) throw new IndexOutOfBoundsException();
        if (transform != null) transform.set((Transform) lightTransforms.elementAt(index));
        return (Light) lights.elementAt(index);
    }

    /* ---------------- rendering ---------------- */
    private static final class Item {
        Node node;
        int submesh;
        int layer;
        float[] world;
        float alpha;
    }

    private Light[] frameLights = new Light[8];
    private float[][] frameLightWorld = new float[8][];
    private int frameLightCount;

    public void render(World world) {
        checkTarget();
        if (world == null) throw new NullPointerException();
        Camera cam = world.activeCamera;
        if (cam == null) throw new IllegalStateException("World has no active camera");
        if (cam.root() != world) throw new IllegalStateException("Active camera is not in the world");
        clear(world.background);
        float[] cw = new float[16];
        cam.worldMatrix(cw);
        Camera savedCam = camera;
        float[] savedWorld = new float[16];
        System.arraycopy(cameraWorld, 0, savedWorld, 0, 16);
        camera = cam;
        System.arraycopy(cw, 0, cameraWorld, 0, 16);
        if (!Transform.invert(cameraWorld, view)) throw new ArithmeticException();
        /* lights in the world */
        frameLightCount = 0;
        float[] id = new float[16];
        new Transform().get(id);
        collectLights(world, id);
        Vector items = new Vector();
        collect(world, id, 1f, cam.scope, items, true);
        drawItems(items, true);
        camera = savedCam;
        System.arraycopy(savedWorld, 0, cameraWorld, 0, 16);
        Transform.invert(cameraWorld, view);
    }

    private void collectLights(Node n, float[] parent) {
        if (!n.rendering) return;
        float[] m = new float[16];
        if (n instanceof World) System.arraycopy(parent, 0, m, 0, 16);
        else {
            n.composite(m);
            Transform.mul(parent, m, m);
        }
        if (n instanceof Light && frameLightCount < 8) {
            frameLights[frameLightCount] = (Light) n;
            frameLightWorld[frameLightCount] = m;
            frameLightCount++;
        }
        if (n instanceof Group) {
            Group g = (Group) n;
            for (int i = 0; i < g.children.size(); i++) collectLights((Node) g.children.elementAt(i), m);
        }
    }

    public void render(Node node, Transform transform) {
        checkTarget();
        if (node == null) throw new NullPointerException();
        if (!(node instanceof Mesh || node instanceof Sprite3D || node instanceof Group)) throw new IllegalArgumentException();
        if (camera == null) throw new IllegalStateException("No camera");
        float[] w = new float[16];
        if (transform != null) System.arraycopy(transform.m, 0, w, 0, 16);
        else new Transform().get(w);
        useImmediateLights();
        Vector items = new Vector();
        collect(node, w, 1f, camera.scope, items, false);
        drawItems(items, false);
    }

    public void render(VertexBuffer vertices, IndexBuffer triangles, Appearance appearance, Transform transform) {
        render(vertices, triangles, appearance, transform, -1);
    }

    public void render(VertexBuffer vertices, IndexBuffer triangles, Appearance appearance, Transform transform, int scope) {
        checkTarget();
        if (vertices == null || triangles == null || appearance == null) throw new NullPointerException();
        if (camera == null) throw new IllegalStateException("No camera");
        if ((scope & camera.scope) == 0) return;
        float[] w = new float[16];
        if (transform != null) System.arraycopy(transform.m, 0, w, 0, 16);
        else new Transform().get(w);
        useImmediateLights();
        drawGeometry(vertices, null, null, null, triangles, appearance, w, 1f, scope);
    }

    private void useImmediateLights() {
        frameLightCount = 0;
        for (int i = 0; i < lights.size() && frameLightCount < 8; i++) {
            Light l = (Light) lights.elementAt(i);
            if (l == null) continue;
            frameLights[frameLightCount] = l;
            frameLightWorld[frameLightCount] = ((Transform) lightTransforms.elementAt(i)).m;
            frameLightCount++;
        }
    }

    /** Collects renderable (node, submesh) pairs under n. `own`: apply n's own transform. */
    private void collect(Node n, float[] parent, float alpha, int camScope, Vector out, boolean own) {
        if (!n.rendering) return;
        float[] m = new float[16];
        if (own && !(n instanceof World)) {
            n.composite(m);
            Transform.mul(parent, m, m);
        } else System.arraycopy(parent, 0, m, 0, 16);
        float a = alpha * n.alphaFactor;
        if (n instanceof Mesh) {
            Mesh mesh = (Mesh) n;
            if ((mesh.scope & camScope) != 0) {
                for (int i = 0; i < mesh.submeshes.length; i++) {
                    Appearance ap = mesh.appearances[i];
                    if (ap == null) continue;
                    Item it = new Item();
                    it.node = mesh;
                    it.submesh = i;
                    it.layer = ap.layer;
                    it.world = m;
                    it.alpha = a;
                    out.addElement(it);
                }
            }
            if (n instanceof SkinnedMesh) collect(((SkinnedMesh) n).skeleton, m, a, camScope, out, true);
        } else if (n instanceof Sprite3D) {
            Sprite3D s = (Sprite3D) n;
            if ((s.scope & camScope) != 0 && s.appearance != null) {
                Item it = new Item();
                it.node = s;
                it.layer = s.appearance.layer;
                it.world = m;
                it.alpha = a;
                out.addElement(it);
            }
        } else if (n instanceof Group) {
            Group g = (Group) n;
            for (int i = 0; i < g.children.size(); i++) collect((Node) g.children.elementAt(i), m, a, camScope, out, true);
        }
    }

    private void drawItems(Vector items, boolean worldMode) {
        int n = items.size();
        Item[] arr = new Item[n];
        items.copyInto(arr);
        /* stable sort by layer; blended geometry after opaque geometry within a layer */
        for (int i = 1; i < n; i++) {
            Item x = arr[i];
            int kx = sortKey(x);
            int j = i - 1;
            while (j >= 0 && sortKey(arr[j]) > kx) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = x;
        }
        Hashtable deformed = new Hashtable();
        for (int i = 0; i < n; i++) {
            Item it = arr[i];
            if (it.node instanceof Mesh) {
                Mesh mesh = (Mesh) it.node;
                if (!deformed.containsKey(mesh)) {
                    mesh.deform();
                    deformed.put(mesh, mesh);
                }
                drawGeometry(mesh.vb, mesh.defPos, mesh.defNrm, mesh.defCol, mesh.submeshes[it.submesh], mesh.appearances[it.submesh],
                             it.world, it.alpha, mesh.scope);
            } else drawSprite((Sprite3D) it.node, it.world, it.alpha);
        }
    }

    private static int sortKey(Item it) {
        Appearance ap = it.node instanceof Mesh ? ((Mesh) it.node).appearances[it.submesh] : ((Sprite3D) it.node).appearance;
        boolean blended = ap.compositing != null && ap.compositing.blending != CompositingMode.REPLACE;
        return it.layer * 2 + (blended ? 1 : 0);
    }

    private void fillLights(int scope) {
        int nl = 0;
        float[] eye = new float[16];
        for (int i = 0; i < frameLightCount; i++) {
            Light l = frameLights[i];
            if ((l.scope & scope) == 0) continue;
            Transform.mul(view, frameLightWorld[i], eye);
            l.pack(eye, fst, M3G.F_LIGHTS + nl * 16);
            nl++;
        }
        ist[M3G.I_NUM_LIGHTS] = nl;
    }

    private void fillAppearance(Appearance ap, float alpha) {
        Material mat = ap.material;
        ist[M3G.I_HAS_MATERIAL] = mat != null ? 1 : 0;
        if (mat != null) {
            ist[M3G.I_MAT_AMBIENT] = 0xFF000000 | mat.ambient;
            ist[M3G.I_MAT_DIFFUSE] = mat.diffuse;
            ist[M3G.I_MAT_EMISSIVE] = 0xFF000000 | mat.emissive;
            ist[M3G.I_MAT_SPECULAR] = 0xFF000000 | mat.specular;
            ist[M3G.I_MAT_TRACKING] = mat.tracking ? 1 : 0;
            fst[M3G.F_SHININESS] = mat.shininess;
        }
        PolygonMode pm = ap.polygon;
        ist[M3G.I_CULLING] = pm != null ? pm.culling : PolygonMode.CULL_BACK;
        ist[M3G.I_WINDING] = pm != null ? pm.winding : PolygonMode.WINDING_CCW;
        ist[M3G.I_SHADING] = pm != null ? pm.shading : PolygonMode.SHADE_SMOOTH;
        ist[M3G.I_TWO_SIDED] = pm != null && pm.twoSided ? 1 : 0;
        ist[M3G.I_LOCAL_CAMERA] = pm != null && pm.localCamera ? 1 : 0;
        CompositingMode cm = ap.compositing;
        ist[M3G.I_BLENDING] = cm != null ? cm.blending : CompositingMode.REPLACE;
        ist[M3G.I_DEPTH_TEST] = (cm == null || cm.depthTest) && depthEnabled ? 1 : 0;
        ist[M3G.I_DEPTH_WRITE] = cm == null || cm.depthWrite ? 1 : 0;
        ist[M3G.I_COLOR_WRITE] = cm == null || cm.colorWrite ? 1 : 0;
        ist[M3G.I_ALPHA_WRITE] = cm == null || cm.alphaWrite ? 1 : 0;
        fst[M3G.F_ALPHA_THRESHOLD] = cm != null ? cm.alphaThreshold : 0;
        fst[M3G.F_DEPTH_FACTOR] = cm != null ? cm.offsetFactor : 0;
        fst[M3G.F_DEPTH_UNITS] = cm != null ? cm.offsetUnits : 0;
        Fog fog = ap.fog;
        ist[M3G.I_FOG_MODE] = fog != null ? fog.mode : 0;
        if (fog != null) {
            ist[M3G.I_FOG_COLOR] = fog.color;
            fst[M3G.F_FOG_NEAR] = fog.near;
            fst[M3G.F_FOG_FAR] = fog.far;
            fst[M3G.F_FOG_DENSITY] = fog.density;
        }
        fst[M3G.F_ALPHA_FACTOR] = alpha;
    }

    private int[] texState(Texture2D tex, int base, int unitF) {
        if (tex == null || tex.image == null) return null;
        Image2D img = tex.image;
        ist[base] = img.width;
        ist[base + 1] = img.height;
        ist[base + 2] = img.formatBits();
        ist[base + 3] = tex.wrapS;
        ist[base + 4] = tex.wrapT;
        ist[base + 5] = tex.imageFilter;
        ist[base + 6] = tex.blending;
        ist[base + 7] = tex.blendColor;
        float[] tm = new float[16];
        tex.composite(tm);
        System.arraycopy(tm, 0, fst, unitF + 4, 16);
        return img.argb;
    }

    private void projection(float[] out) {
        float aspect = (float) vpW / vpH;
        camera.projectionMatrix(out, aspect);
    }

    private void drawGeometry(VertexBuffer vb, float[] defPos, float[] defNrm, short[] defCol, IndexBuffer ib, Appearance ap,
                              float[] world, float alpha, int scope) {
        if (vb.positions == null && defPos == null) return;
        int nv = vb.getVertexCount();
        baseState();
        fillAppearance(ap, alpha);
        fillLights(scope);
        float[] mv = new float[16];
        Transform.mul(view, world, mv);
        System.arraycopy(mv, 0, fst, M3G.F_MV, 16);
        float[] pj = new float[16];
        projection(pj);
        System.arraycopy(pj, 0, fst, M3G.F_PROJ, 16);
        ist[M3G.I_VCOUNT] = nv;
        fst[M3G.F_POS_SCALE] = vb.posScale;
        fst[M3G.F_POS_BIAS] = vb.posBias[0];
        fst[M3G.F_POS_BIAS + 1] = vb.posBias[1];
        fst[M3G.F_POS_BIAS + 2] = vb.posBias[2];
        fst[M3G.F_NRM_SCALE] = 1;
        ist[M3G.I_DEFAULT_COLOR] = vb.defaultColor;
        short[] col = defCol != null ? defCol : (vb.colors != null ? vb.colors.data : null);
        ist[M3G.I_COL_COMPS] = vb.colors != null ? vb.colors.components : 0;
        int[] tex0 = null, tex1 = null;
        short[] tc0 = null, tc1 = null;
        Texture2D t0 = ap.textures[0], t1 = ap.textures[1];
        if (t0 != null && vb.texCoords[0] != null) {
            tex0 = texState(t0, M3G.I_TEX0, M3G.F_TC0);
            tc0 = vb.texCoords[0].data;
            ist[M3G.I_TC0_COMPS] = vb.texCoords[0].components;
            fst[M3G.F_TC0] = vb.texScale[0];
            System.arraycopy(vb.texBias[0], 0, fst, M3G.F_TC0 + 1, 3);
        }
        if (t1 != null && vb.texCoords[1] != null) {
            int[] tx = texState(t1, tex0 != null ? M3G.I_TEX1 : M3G.I_TEX0, tex0 != null ? M3G.F_TC1 : M3G.F_TC0);
            short[] tc = vb.texCoords[1].data;
            if (tex0 != null) {
                tex1 = tx;
                tc1 = tc;
                ist[M3G.I_TC1_COMPS] = vb.texCoords[1].components;
                fst[M3G.F_TC1] = vb.texScale[1];
                System.arraycopy(vb.texBias[1], 0, fst, M3G.F_TC1 + 1, 3);
            } else {
                tex0 = tx;
                tc0 = tc;
                ist[M3G.I_TC0_COMPS] = vb.texCoords[1].components;
                fst[M3G.F_TC0] = vb.texScale[1];
                System.arraycopy(vb.texBias[1], 0, fst, M3G.F_TC0 + 1, 3);
            }
        }
        int[] tris = ib.triangles();
        M3G.drawTriangles(pixels, depth, tw, th, ist, fst, defPos != null ? null : vb.positions.data, defPos,
                          defNrm != null ? null : (vb.normals != null ? vb.normals.data : null), defNrm, col, tc0, tex0, tc1, tex1, tris,
                          tris.length);
    }

    private static final int[] QUAD = {0, 1, 2, 0, 2, 3};
    private final short[] quadTc = new short[8];

    private void drawSprite(Sprite3D s, float[] world, float alpha) {
        Image2D img = s.image;
        if (img == null || s.cropW == 0 || s.cropH == 0) return;
        float[] mv = new float[16];
        Transform.mul(view, world, mv);
        float[] pj = new float[16];
        projection(pj);
        float ox = mv[3], oy = mv[7], oz = mv[11];
        float hx, hy;
        if (s.scaled) {
            hx = 0.5f * (float) Math.sqrt(mv[0] * mv[0] + mv[4] * mv[4] + mv[8] * mv[8]);
            hy = 0.5f * (float) Math.sqrt(mv[1] * mv[1] + mv[5] * mv[5] + mv[9] * mv[9]);
        } else {
            float w = pj[12] * ox + pj[13] * oy + pj[14] * oz + pj[15];
            if (w <= 0 || pj[0] == 0 || pj[5] == 0) return;
            hx = Math.abs((float) s.cropW / vpW * w / pj[0]);
            hy = Math.abs((float) s.cropH / vpH * w / pj[5]);
        }
        float[] pos = {ox - hx, oy - hy, oz, ox + hx, oy - hy, oz, ox + hx, oy + hy, oz, ox - hx, oy + hy, oz};
        baseState();
        Appearance ap = s.appearance;
        fillAppearance(ap, alpha);
        ist[M3G.I_HAS_MATERIAL] = 0;
        ist[M3G.I_CULLING] = PolygonMode.CULL_NONE;
        ist[M3G.I_SHADING] = PolygonMode.SHADE_SMOOTH;
        ist[M3G.I_NUM_LIGHTS] = 0;
        new Transform().get(mv);
        System.arraycopy(mv, 0, fst, M3G.F_MV, 16);
        System.arraycopy(pj, 0, fst, M3G.F_PROJ, 16);
        ist[M3G.I_VCOUNT] = 4;
        ist[M3G.I_COL_COMPS] = 0;
        ist[M3G.I_DEFAULT_COLOR] = 0xFFFFFFFF;
        /* texture coordinates in texels, scaled by 1/size */
        int x0 = s.cropX, y0 = s.cropY, x1 = s.cropX + s.cropW, y1 = s.cropY + s.cropH;
        quadTc[0] = (short) x0;
        quadTc[1] = (short) y1;
        quadTc[2] = (short) x1;
        quadTc[3] = (short) y1;
        quadTc[4] = (short) x1;
        quadTc[5] = (short) y0;
        quadTc[6] = (short) x0;
        quadTc[7] = (short) y0;
        ist[M3G.I_TEX0] = img.width;
        ist[M3G.I_TEX0 + 1] = img.height;
        ist[M3G.I_TEX0 + 2] = img.formatBits();
        ist[M3G.I_TEX0 + 3] = Texture2D.WRAP_CLAMP;
        ist[M3G.I_TEX0 + 4] = Texture2D.WRAP_CLAMP;
        ist[M3G.I_TEX0 + 5] = Texture2D.FILTER_NEAREST;
        ist[M3G.I_TEX0 + 6] = Texture2D.FUNC_MODULATE; /* white vertex color * texture keeps the alpha factor */
        ist[M3G.I_TEX0 + 7] = 0;
        ist[M3G.I_TC0_COMPS] = 2;
        fst[M3G.F_TC0] = 1;
        fst[M3G.F_TC0 + 1] = fst[M3G.F_TC0 + 2] = fst[M3G.F_TC0 + 3] = 0;
        float[] tm = new float[16];
        new Transform().get(tm);
        tm[0] = 1f / img.width;
        tm[5] = 1f / img.height;
        System.arraycopy(tm, 0, fst, M3G.F_TC0 + 4, 16);
        M3G.drawTriangles(pixels, depth, tw, th, ist, fst, null, pos, null, null, null, quadTc, img.argb, null, null, QUAD, 6);
    }
}
