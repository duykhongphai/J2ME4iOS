package javax.microedition.m3g;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;

/** Loads M3G (JSR-184) files and PNG images. */
public class Loader {
    private static final byte[] ID = {(byte) 0xAB, 0x4A, 0x53, 0x52, 0x31, 0x38, 0x34, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A};

    private Loader() {}

    public static Object3D[] load(String name) throws IOException {
        if (name == null) throw new NullPointerException();
        return loadData(readName(name), name);
    }

    public static Object3D[] load(byte[] data, int offset) throws IOException {
        if (data == null) throw new NullPointerException();
        if (offset < 0 || offset >= data.length) throw new IndexOutOfBoundsException();
        byte[] d = data;
        if (offset != 0) {
            d = new byte[data.length - offset];
            System.arraycopy(data, offset, d, 0, d.length);
        }
        return loadData(d, null);
    }

    private static byte[] readName(String name) throws IOException {
        if (name.indexOf("://") > 0 && !name.startsWith("resource:")) {
            InputStream in = javax.microedition.io.Connector.openInputStream(name);
            try {
                ByteArrayOutputStream bo = new ByteArrayOutputStream(16384);
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf, 0, buf.length)) > 0) bo.write(buf, 0, n);
                return bo.toByteArray();
            } finally {
                in.close();
            }
        }
        String p = name.startsWith("resource:") ? name.substring(9) : name;
        while (p.startsWith("/")) p = p.substring(1);
        byte[] d = j4i.Sys.getResource(p);
        if (d == null) throw new IOException("Resource not found: " + name);
        return d;
    }

    private static Object3D[] loadData(byte[] d, String name) throws IOException {
        if (d.length >= 8 && (d[0] & 255) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return new Object3D[] {loadPng(d)};
        for (int i = 0; i < ID.length; i++)
            if (d.length <= i || d[i] != ID[i]) throw new IOException("Not an M3G or PNG file" + (name != null ? ": " + name : ""));
        try {
            return new Loader().parseFile(d, name);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IOException("Truncated M3G file");
        } catch (ClassCastException e) {
            throw new IOException("Bad object reference in M3G file");
        } catch (NullPointerException e) {
            throw new IOException("Missing object in M3G file");
        }
    }

    private static Image2D loadPng(byte[] d) throws IOException {
        javax.microedition.lcdui.Image img;
        try {
            img = javax.microedition.lcdui.Image.createImage(d, 0, d.length);
        } catch (IllegalArgumentException e) {
            throw new IOException("Bad PNG");
        }
        int[] px = javax.microedition.lcdui.J4IBridge.pixels(img);
        boolean alpha = false;
        for (int i = 0; i < px.length && !alpha; i++)
            if ((px[i] >>> 24) != 255) alpha = true;
        return new Image2D(alpha ? Image2D.RGBA : Image2D.RGB, img);
    }

    /* ---------------- M3G parsing ---------------- */
    private final Vector objects = new Vector();
    private final Hashtable referenced = new Hashtable();
    private byte[] b;
    private int p;

    private Object3D[] parseFile(byte[] d, String name) throws IOException {
        objects.addElement(null); /* index 0 = null */
        int pos = ID.length;
        while (pos < d.length) {
            int compression = d[pos] & 255;
            int total = le32(d, pos + 1);
            int uncompressed = le32(d, pos + 5);
            int dataLen = total - 13;
            if (dataLen < 0 || pos + total > d.length) throw new IOException("Bad section length");
            byte[] sec;
            if (compression == 0) {
                sec = new byte[dataLen];
                System.arraycopy(d, pos + 9, sec, 0, dataLen);
            } else if (compression == 1) {
                sec = j4i.Sys.inflate(d, pos + 9, dataLen, uncompressed);
                if (sec == null) throw new IOException("Cannot decompress section");
            } else throw new IOException("Unknown compression " + compression);
            parseSection(sec, name);
            pos += total;
        }
        Vector roots = new Vector();
        for (int i = 1; i < objects.size(); i++) {
            Object o = objects.elementAt(i);
            if (o instanceof Object3D && !referenced.containsKey(o)) roots.addElement(o);
        }
        Object3D[] r = new Object3D[roots.size()];
        roots.copyInto(r);
        return r;
    }

    private static int le32(byte[] d, int o) {
        return (d[o] & 255) | ((d[o + 1] & 255) << 8) | ((d[o + 2] & 255) << 16) | ((d[o + 3] & 255) << 24);
    }

    private void parseSection(byte[] sec, String name) throws IOException {
        b = sec;
        p = 0;
        while (p < b.length) {
            int type = u8();
            int len = i32();
            int end = p + len;
            Object o;
            switch (type) {
                case 0: o = "header"; break;
                case 1: o = animationController(); break;
                case 2: o = animationTrack(); break;
                case 3: o = appearance(); break;
                case 4: o = background(); break;
                case 5: o = camera(); break;
                case 6: o = compositingMode(); break;
                case 7: o = fog(); break;
                case 8: o = image2D(); break;
                case 9: o = group(new Group()); break;
                case 10: o = light(); break;
                case 11: o = material(); break;
                case 12: o = mesh(); break;
                case 13: o = morphingMesh(); break;
                case 14: o = polygonMode(); break;
                case 15: o = skinnedMesh(); break;
                case 16: o = sprite3D(); break;
                case 17: o = texture2D(); break;
                case 18: o = triangleStripArray(); break;
                case 19: o = vertexArray(); break;
                case 20: o = vertexBuffer(); break;
                case 21: o = world(); break;
                case 22: o = keyframeSequence(); break;
                case 255: o = external(name); break;
                default: throw new IOException("Unknown M3G object type " + type);
            }
            objects.addElement(o);
            p = end;
        }
    }

    /* ---- primitive readers (little endian) ---- */
    private int u8() { return b[p++] & 255; }

    private int i8() { return b[p++]; }

    private boolean bool() { return b[p++] != 0; }

    private int u16() {
        int v = (b[p] & 255) | ((b[p + 1] & 255) << 8);
        p += 2;
        return v;
    }

    private short i16() { return (short) u16(); }

    private int i32() {
        int v = le32(b, p);
        p += 4;
        return v;
    }

    private float f32() { return Float.intBitsToFloat(i32()); }

    private String str() throws IOException {
        int s = p;
        while (b[p] != 0) p++;
        String r = new String(b, s, p - s, "UTF-8");
        p++;
        return r;
    }

    private int rgb() {
        int v = 0xFF000000 | (u8() << 16);
        v |= u8() << 8;
        return v | u8();
    }

    private int rgba() {
        int r = u8(), g = u8(), bl = u8(), a = u8();
        return (a << 24) | (r << 16) | (g << 8) | bl;
    }

    private Object ref() throws IOException {
        int idx = i32();
        if (idx == 0) return null;
        if (idx < 0 || idx >= objects.size()) throw new IOException("Forward or bad reference " + idx);
        Object o = objects.elementAt(idx);
        if (o instanceof Object3D) referenced.put(o, o);
        return o;
    }

    private float[] floats(int n) {
        float[] f = new float[n];
        for (int i = 0; i < n; i++) f[i] = f32();
        return f;
    }

    private byte[] byteArray() {
        int n = i32();
        byte[] r = new byte[n];
        System.arraycopy(b, p, r, 0, n);
        p += n;
        return r;
    }

    /* ---- object readers ---- */
    private Object external(String name) throws IOException {
        String uri = str();
        String path = uri;
        if (name != null && uri.indexOf(':') < 0 && !uri.startsWith("/")) {
            int slash = name.lastIndexOf('/');
            if (slash >= 0) path = name.substring(0, slash + 1) + uri;
        }
        Object3D[] r = load(path);
        return r.length > 0 ? r[0] : null;
    }

    private void object3D(Object3D o) throws IOException {
        o.userID = i32();
        int n = i32();
        for (int i = 0; i < n; i++) {
            Object t = ref();
            if (t instanceof AnimationTrack) o.tracks.addElement(t);
        }
        int params = i32();
        if (params > 0) {
            Hashtable h = new Hashtable();
            for (int i = 0; i < params; i++) {
                int id = i32();
                h.put(new Integer(id), byteArray());
            }
            o.userObject = h;
        }
    }

    private void transformable(Transformable t) throws IOException {
        object3D(t);
        if (bool()) {
            t.tx = f32();
            t.ty = f32();
            t.tz = f32();
            t.sx = f32();
            t.sy = f32();
            t.sz = f32();
            float angle = f32();
            float ax = f32(), ay = f32(), az = f32();
            if (angle != 0 && (ax != 0 || ay != 0 || az != 0)) Quat.fromAxisAngle(angle, ax, ay, az, t.q);
        }
        if (bool()) {
            Transform m = new Transform();
            m.set(floats(16));
            t.general = m;
        }
    }

    private void node(Node n) throws IOException {
        transformable(n);
        n.rendering = bool();
        n.picking = bool();
        n.alphaFactor = u8() / 255f;
        n.scope = i32();
        if (bool()) {
            n.zTarget = u8();
            n.yTarget = u8();
            n.zRef = (Node) ref();
            n.yRef = (Node) ref();
        }
    }

    private Object animationController() throws IOException {
        AnimationController c = new AnimationController();
        object3D(c);
        c.speed = f32();
        c.weight = f32();
        c.activeStart = i32();
        c.activeEnd = i32();
        c.refSequenceTime = f32();
        c.refWorldTime = i32();
        return c;
    }

    private Object animationTrack() throws IOException {
        int start = p;
        /* fields after Object3D are needed for the constructor: read ahead */
        Object3D tmp = new Group();
        object3D(tmp);
        KeyframeSequence ks = (KeyframeSequence) ref();
        AnimationController ac = (AnimationController) ref();
        int prop = i32();
        AnimationTrack t = new AnimationTrack(ks, prop);
        t.userID = tmp.userID;
        t.userObject = tmp.userObject;
        t.tracks = tmp.tracks;
        t.controller = ac;
        return t;
    }

    private Object appearance() throws IOException {
        Appearance a = new Appearance();
        object3D(a);
        a.layer = i8();
        a.compositing = (CompositingMode) ref();
        a.fog = (Fog) ref();
        a.polygon = (PolygonMode) ref();
        a.material = (Material) ref();
        int n = i32();
        for (int i = 0; i < n; i++) {
            Texture2D t = (Texture2D) ref();
            if (i < a.textures.length) a.textures[i] = t;
        }
        return a;
    }

    private Object background() throws IOException {
        Background bg = new Background();
        object3D(bg);
        bg.color = rgba();
        bg.image = (Image2D) ref();
        bg.modeX = u8();
        bg.modeY = u8();
        bg.cropX = i32();
        bg.cropY = i32();
        bg.cropW = i32();
        bg.cropH = i32();
        bg.depthClear = bool();
        bg.colorClear = bool();
        return bg;
    }

    private Object camera() throws IOException {
        Camera c = new Camera();
        node(c);
        c.projection = u8();
        if (c.projection == Camera.GENERIC) c.generic.set(floats(16));
        else {
            c.fovy = f32();
            c.aspect = f32();
            c.near = f32();
            c.far = f32();
        }
        return c;
    }

    private Object compositingMode() throws IOException {
        CompositingMode c = new CompositingMode();
        object3D(c);
        c.depthTest = bool();
        c.depthWrite = bool();
        c.colorWrite = bool();
        c.alphaWrite = bool();
        c.blending = u8();
        c.alphaThreshold = u8() / 255f;
        c.offsetFactor = f32();
        c.offsetUnits = f32();
        return c;
    }

    private Object fog() throws IOException {
        Fog f = new Fog();
        object3D(f);
        f.color = rgb() & 0xFFFFFF;
        f.mode = u8();
        if (f.mode == Fog.EXPONENTIAL) f.density = f32();
        else {
            f.near = f32();
            f.far = f32();
        }
        return f;
    }

    private Object image2D() throws IOException {
        int start = p;
        Object3D tmp = new Group();
        object3D(tmp);
        int format = u8();
        boolean mutable = bool();
        int w = i32(), h = i32();
        Image2D img;
        if (mutable) img = new Image2D(format, w, h);
        else {
            byte[] palette = byteArray();
            byte[] pixels = byteArray();
            img = palette.length > 0 ? new Image2D(format, w, h, pixels, palette) : new Image2D(format, w, h, pixels);
        }
        img.userID = tmp.userID;
        img.userObject = tmp.userObject;
        img.tracks = tmp.tracks;
        return img;
    }

    private Group group(Group g) throws IOException {
        node(g);
        int n = i32();
        for (int i = 0; i < n; i++) {
            Node c = (Node) ref();
            if (c == null) continue;
            if (c.parent != null && c.parent != g) {
                if (c.parent instanceof Group) ((Group) c.parent).children.removeElement(c);
                c.parent = null;
            }
            g.addChild(c);
        }
        return g;
    }

    private Object light() throws IOException {
        Light l = new Light();
        node(l);
        l.attConst = f32();
        l.attLinear = f32();
        l.attQuad = f32();
        l.color = rgb() & 0xFFFFFF;
        l.mode = u8();
        l.intensity = f32();
        l.spotAngle = f32();
        l.spotExponent = f32();
        return l;
    }

    private Object material() throws IOException {
        Material m = new Material();
        object3D(m);
        m.ambient = rgb() & 0xFFFFFF;
        m.diffuse = rgba();
        m.emissive = rgb() & 0xFFFFFF;
        m.specular = rgb() & 0xFFFFFF;
        m.shininess = f32();
        m.tracking = bool();
        return m;
    }

    private void meshBody(Mesh m) throws IOException {
        node(m);
        m.vb = (VertexBuffer) ref();
        int n = i32();
        m.submeshes = new IndexBuffer[n];
        m.appearances = new Appearance[n];
        for (int i = 0; i < n; i++) {
            m.submeshes[i] = (IndexBuffer) ref();
            m.appearances[i] = (Appearance) ref();
        }
        if (m.vb == null) throw new IOException("Mesh without vertex buffer");
    }

    private Object mesh() throws IOException {
        Mesh m = new Mesh();
        meshBody(m);
        return m;
    }

    private Object morphingMesh() throws IOException {
        Mesh base = new Mesh();
        meshBody(base);
        int n = i32();
        VertexBuffer[] targets = new VertexBuffer[n];
        float[] w = new float[n];
        for (int i = 0; i < n; i++) {
            targets[i] = (VertexBuffer) ref();
            w[i] = f32();
        }
        MorphingMesh m = new MorphingMesh(base.vb, targets, base.submeshes, base.appearances);
        base.copyNodeTo(m);
        copyIds(base, m);
        m.setWeights(w);
        return m;
    }

    private Object skinnedMesh() throws IOException {
        Mesh base = new Mesh();
        meshBody(base);
        Group skeleton = (Group) ref();
        if (skeleton.parent != null) {
            if (skeleton.parent instanceof Group) ((Group) skeleton.parent).children.removeElement(skeleton);
            skeleton.parent = null;
        }
        SkinnedMesh m = new SkinnedMesh(base.vb, base.submeshes, base.appearances, skeleton);
        base.copyNodeTo(m);
        copyIds(base, m);
        int n = i32();
        for (int i = 0; i < n; i++) {
            Node bone = (Node) ref();
            int first = i32(), count = i32(), weight = i32();
            if (bone != null && count > 0 && weight > 0) m.addTransform(bone, weight, first, count);
        }
        return m;
    }

    private static void copyIds(Object3D from, Object3D to) {
        to.userID = from.userID;
        to.userObject = from.userObject;
        to.tracks = from.tracks;
    }

    private Object polygonMode() throws IOException {
        PolygonMode m = new PolygonMode();
        object3D(m);
        m.culling = u8();
        m.shading = u8();
        m.winding = u8();
        m.twoSided = bool();
        m.localCamera = bool();
        m.perspective = bool();
        return m;
    }

    private Object sprite3D() throws IOException {
        Group tmp = new Group();
        node(tmp);
        Image2D img = (Image2D) ref();
        Appearance ap = (Appearance) ref();
        boolean scaled = bool();
        Sprite3D s = new Sprite3D(scaled, img, ap);
        tmp.copyNodeTo(s);
        copyIds(tmp, s);
        s.cropX = i32();
        s.cropY = i32();
        s.cropW = i32();
        s.cropH = i32();
        return s;
    }

    private Object texture2D() throws IOException {
        Group tmp = new Group();
        transformable(tmp);
        Image2D img = (Image2D) ref();
        Texture2D t = new Texture2D(img);
        tmp.copyTransformTo(t);
        copyIds(tmp, t);
        t.blendColor = rgb() & 0xFFFFFF;
        t.blending = u8();
        t.wrapS = u8();
        t.wrapT = u8();
        t.levelFilter = u8();
        t.imageFilter = u8();
        return t;
    }

    private Object triangleStripArray() throws IOException {
        Group tmp = new Group();
        object3D(tmp);
        int enc = u8();
        int first = 0;
        int[] indices = null;
        switch (enc) {
            case 0: first = i32(); break;
            case 1: first = u8(); break;
            case 2: first = u16(); break;
            case 128: {
                int n = i32();
                indices = new int[n];
                for (int i = 0; i < n; i++) indices[i] = i32();
                break;
            }
            case 129: {
                int n = i32();
                indices = new int[n];
                for (int i = 0; i < n; i++) indices[i] = u8();
                break;
            }
            case 130: {
                int n = i32();
                indices = new int[n];
                for (int i = 0; i < n; i++) indices[i] = u16();
                break;
            }
            default: throw new IOException("Bad index encoding " + enc);
        }
        int ns = i32();
        int[] strips = new int[ns];
        for (int i = 0; i < ns; i++) strips[i] = i32();
        TriangleStripArray t = indices != null ? new TriangleStripArray(indices, strips) : new TriangleStripArray(first, strips);
        copyIds(tmp, t);
        return t;
    }

    private Object vertexArray() throws IOException {
        Group tmp = new Group();
        object3D(tmp);
        int size = u8(), comps = u8(), enc = u8(), count = u16();
        VertexArray va = new VertexArray(count, comps, size);
        short[] d = va.data;
        if (size == 1) {
            for (int i = 0; i < count * comps; i++) {
                int v = i8();
                if (enc == 1 && i >= comps) v = (byte) (d[i - comps] + v);
                d[i] = (short) v;
            }
        } else {
            for (int i = 0; i < count * comps; i++) {
                int v = i16();
                if (enc == 1 && i >= comps) v = (short) (d[i - comps] + v);
                d[i] = (short) v;
            }
        }
        copyIds(tmp, va);
        return va;
    }

    private Object vertexBuffer() throws IOException {
        VertexBuffer vb = new VertexBuffer();
        object3D(vb);
        vb.defaultColor = rgba();
        vb.positions = (VertexArray) ref();
        float[] bias = floats(3);
        vb.posScale = f32();
        System.arraycopy(bias, 0, vb.posBias, 0, 3);
        vb.normals = (VertexArray) ref();
        vb.colors = (VertexArray) ref();
        int n = i32();
        for (int i = 0; i < n; i++) {
            VertexArray tc = (VertexArray) ref();
            float[] tb = floats(3);
            float ts = f32();
            if (i < VertexBuffer.MAX_UNITS) {
                vb.texCoords[i] = tc;
                System.arraycopy(tb, 0, vb.texBias[i], 0, 3);
                vb.texScale[i] = ts;
            }
        }
        return vb;
    }

    private Object world() throws IOException {
        World w = new World();
        group(w);
        w.activeCamera = (Camera) ref();
        w.background = (Background) ref();
        return w;
    }

    private Object keyframeSequence() throws IOException {
        Group tmp = new Group();
        object3D(tmp);
        int interp = u8(), repeat = u8(), enc = u8();
        int duration = i32(), first = i32(), last = i32(), comps = i32(), count = i32();
        KeyframeSequence ks = new KeyframeSequence(Math.max(1, count), comps, interp);
        float[] v = new float[comps];
        if (enc == 0) {
            for (int k = 0; k < count; k++) {
                int t = i32();
                for (int c = 0; c < comps; c++) v[c] = f32();
                ks.times[k] = t;
                System.arraycopy(v, 0, ks.values, k * comps, comps);
            }
        } else {
            float[] bias = floats(comps), scale = floats(comps);
            for (int k = 0; k < count; k++) {
                int t = i32();
                for (int c = 0; c < comps; c++) {
                    float raw = enc == 1 ? u8() / 255f : u16() / 65535f;
                    v[c] = bias[c] + scale[c] * raw;
                }
                ks.times[k] = t;
                System.arraycopy(v, 0, ks.values, k * comps, comps);
            }
        }
        if (interp == KeyframeSequence.SLERP || interp == KeyframeSequence.SQUAD) {
            float[] q = new float[4];
            for (int k = 0; k < count; k++) {
                System.arraycopy(ks.values, k * 4, q, 0, 4);
                Quat.normalize(q);
                System.arraycopy(q, 0, ks.values, k * 4, 4);
            }
        }
        ks.repeatMode = repeat;
        ks.duration = duration;
        if (count > 0) {
            ks.validFirst = Math.min(first, count - 1);
            ks.validLast = Math.min(last, count - 1);
        }
        copyIds(tmp, ks);
        return ks;
    }
}
