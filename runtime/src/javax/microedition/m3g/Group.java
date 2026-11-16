package javax.microedition.m3g;

import java.util.Vector;

public class Group extends Node {
    final Vector children = new Vector();

    public Group() {}

    Object3D dup() {
        Group g = new Group();
        copyGroupTo(g);
        return g;
    }

    void copyGroupTo(Group g) {
        copyNodeTo(g);
        for (int i = 0; i < children.size(); i++) g.addChild((Node) ((Node) children.elementAt(i)).duplicate());
    }

    int refs(Object3D[] out, int pos) {
        pos = super.refs(out, pos);
        for (int i = 0; i < children.size(); i++) pos = add(out, pos, (Object3D) children.elementAt(i));
        return pos;
    }

    public void addChild(Node child) {
        if (child == null) throw new NullPointerException();
        if (child == this || child instanceof World) throw new IllegalArgumentException();
        if (child.parent != null && child.parent != this) throw new IllegalArgumentException("Node already has a parent");
        for (Node p = this; p != null; p = p.parent)
            if (p == child) throw new IllegalArgumentException("Cycle");
        if (child.parent == this) return;
        child.parent = this;
        children.addElement(child);
    }

    public void removeChild(Node child) {
        if (child == null || child.parent != this) return;
        children.removeElement(child);
        child.parent = null;
    }

    public int getChildCount() { return children.size(); }

    public Node getChild(int index) {
        if (index < 0 || index >= children.size()) throw new IndexOutOfBoundsException();
        return (Node) children.elementAt(index);
    }

    void alignTree(Node reference) {
        alignSelf(reference);
        for (int i = 0; i < children.size(); i++) ((Node) children.elementAt(i)).alignTree(reference);
    }

    public boolean pick(int scope, float x, float y, Camera camera, RayIntersection ri) {
        if (camera == null) throw new NullPointerException();
        if (camera.root() != root()) throw new IllegalStateException("Camera not in the same scene");
        float[] proj = new float[16];
        camera.projectionMatrix(proj, 1);
        float[] inv = new float[16];
        if (!Transform.invert(proj, inv)) return false;
        float ndcX = 2 * x - 1, ndcY = 1 - 2 * y;
        float[] pn = mul4(inv, ndcX, ndcY, -1, 1), pf = mul4(inv, ndcX, ndcY, 1, 1);
        for (int i = 0; i < 3; i++) {
            pn[i] /= pn[3];
            pf[i] /= pf[3];
        }
        /* camera space -> this group's space */
        float[] t = new float[16];
        Transform tr = new Transform();
        camera.getTransformTo(this, tr);
        System.arraycopy(tr.m, 0, t, 0, 16);
        float[] o = mul4(t, pn[0], pn[1], pn[2], 1), f = mul4(t, pf[0], pf[1], pf[2], 1);
        boolean hit = pick(scope, o[0], o[1], o[2], f[0] - o[0], f[1] - o[1], f[2] - o[2], ri);
        return hit;
    }

    static float[] mul4(float[] m, float x, float y, float z, float w) {
        float[] r = new float[4];
        for (int i = 0; i < 4; i++) r[i] = m[i * 4] * x + m[i * 4 + 1] * y + m[i * 4 + 2] * z + m[i * 4 + 3] * w;
        return r;
    }

    public boolean pick(int scope, float ox, float oy, float oz, float dx, float dy, float dz, RayIntersection ri) {
        if (dx == 0 && dy == 0 && dz == 0) throw new IllegalArgumentException();
        Picker p = new Picker(scope, new float[] {ox, oy, oz}, new float[] {dx, dy, dz});
        float[] id = new float[16];
        new Transform().get(id);
        p.visitChildren(this, id);
        if (p.best == null) return false;
        if (ri != null) ri.set(p.best, p.bestT, p.ray(), p.normal, p.submesh, p.texS, p.texT);
        return true;
    }

    /** Ray picking against meshes and sprites, in the coordinates of the picking group. */
    static final class Picker {
        final int scope;
        final float[] o, d;
        Node best;
        float bestT = Float.MAX_VALUE;
        float[] normal = new float[3];
        int submesh;
        float[] texS = new float[2], texT = new float[2];

        Picker(int scope, float[] o, float[] d) {
            this.scope = scope;
            this.o = o;
            this.d = d;
        }

        float[] ray() { return new float[] {o[0], o[1], o[2], d[0], d[1], d[2]}; }

        void visitChildren(Group g, float[] m) {
            for (int i = 0; i < g.children.size(); i++) visit((Node) g.children.elementAt(i), m);
        }

        void visit(Node n, float[] parent) {
            if (!n.picking) return;
            float[] m = new float[16];
            n.composite(m);
            Transform.mul(parent, m, m);
            if (n instanceof Group) visitChildren((Group) n, m);
            else if (n instanceof Mesh && (n.scope & scope) != 0) pickMesh((Mesh) n, m);
        }

        void pickMesh(Mesh mesh, float[] m) {
            VertexBuffer vb = mesh.vb;
            if (vb == null || vb.positions == null) return;
            float[] inv = new float[16];
            if (!Transform.invert(m, inv)) return;
            float[] lo = mul4(inv, o[0], o[1], o[2], 1), ld = mul4(inv, d[0], d[1], d[2], 0);
            short[] p = vb.positions.data;
            float sc = vb.posScale;
            float[] b = vb.posBias;
            int cc = vb.positions.components;
            for (int s = 0; s < mesh.submeshes.length; s++) {
                IndexBuffer ib = mesh.submeshes[s];
                if (ib == null) continue;
                int[] tri = ib.triangles();
                for (int k = 0; k + 2 < tri.length; k += 3) {
                    float[] v0 = vtx(p, tri[k], cc, sc, b), v1 = vtx(p, tri[k + 1], cc, sc, b), v2 = vtx(p, tri[k + 2], cc, sc, b);
                    float[] e1 = {v1[0] - v0[0], v1[1] - v0[1], v1[2] - v0[2]}, e2 = {v2[0] - v0[0], v2[1] - v0[1], v2[2] - v0[2]};
                    float[] pv = new float[3];
                    cross(ld, e2, pv);
                    float det = e1[0] * pv[0] + e1[1] * pv[1] + e1[2] * pv[2];
                    if (Math.abs(det) < 1e-12f) continue;
                    float invDet = 1 / det;
                    float[] tv = {lo[0] - v0[0], lo[1] - v0[1], lo[2] - v0[2]};
                    float u = (tv[0] * pv[0] + tv[1] * pv[1] + tv[2] * pv[2]) * invDet;
                    if (u < 0 || u > 1) continue;
                    float[] qv = new float[3];
                    cross(tv, e1, qv);
                    float v = (ld[0] * qv[0] + ld[1] * qv[1] + ld[2] * qv[2]) * invDet;
                    if (v < 0 || u + v > 1) continue;
                    float t = (e2[0] * qv[0] + e2[1] * qv[1] + e2[2] * qv[2]) * invDet;
                    if (t < 0 || t >= bestT) continue;
                    bestT = t;
                    best = mesh;
                    submesh = s;
                    float[] nn = new float[3];
                    cross(e1, e2, nn);
                    float[] wn = mul4(transposeInv(m), nn[0], nn[1], nn[2], 0);
                    norm(wn, normal);
                    for (int unit = 0; unit < 2; unit++) {
                        VertexArray ta = unit < vb.texCoords.length ? vb.texCoords[unit] : null;
                        if (ta == null) {
                            texS[unit] = texT[unit] = 0;
                            continue;
                        }
                        int tc = ta.components;
                        float ts = vb.texScale[unit];
                        float[] tb = vb.texBias[unit];
                        float s0 = ta.data[tri[k] * tc] * ts + tb[0], s1 = ta.data[tri[k + 1] * tc] * ts + tb[0], s2 = ta.data[tri[k + 2] * tc] * ts + tb[0];
                        float t0 = ta.data[tri[k] * tc + 1] * ts + tb[1], t1 = ta.data[tri[k + 1] * tc + 1] * ts + tb[1], t2 = ta.data[tri[k + 2] * tc + 1] * ts + tb[1];
                        texS[unit] = s0 * (1 - u - v) + s1 * u + s2 * v;
                        texT[unit] = t0 * (1 - u - v) + t1 * u + t2 * v;
                    }
                }
            }
        }

        static float[] transposeInv(float[] m) {
            float[] r = new float[16];
            if (!Transform.invert(m, r)) return m;
            float[] t = new float[16];
            for (int i = 0; i < 4; i++)
                for (int j = 0; j < 4; j++) t[i * 4 + j] = r[j * 4 + i];
            return t;
        }

        static float[] vtx(short[] p, int i, int cc, float sc, float[] b) {
            return new float[] {p[i * cc] * sc + b[0], p[i * cc + 1] * sc + b[1], (cc > 2 ? p[i * cc + 2] : 0) * sc + b[2]};
        }
    }
}
