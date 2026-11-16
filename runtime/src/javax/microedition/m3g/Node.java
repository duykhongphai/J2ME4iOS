package javax.microedition.m3g;

public abstract class Node extends Transformable {
    public static final int NONE = 144;
    public static final int ORIGIN = 145;
    public static final int X_AXIS = 146;
    public static final int Y_AXIS = 147;
    public static final int Z_AXIS = 148;

    Node parent;
    boolean rendering = true, picking = true;
    float alphaFactor = 1;
    int scope = -1;
    int zTarget = NONE, yTarget = NONE;
    Node zRef, yRef;

    Node() {}

    void copyNodeTo(Node o) {
        copyTransformTo(o);
        o.rendering = rendering;
        o.picking = picking;
        o.alphaFactor = alphaFactor;
        o.scope = scope;
        o.zTarget = zTarget;
        o.yTarget = yTarget;
        o.zRef = zRef;
        o.yRef = yRef;
    }

    int refs(Object3D[] out, int pos) {
        pos = super.refs(out, pos);
        if (zRef != null && zRef != this) pos = add(out, pos, zRef);
        if (yRef != null && yRef != this && yRef != zRef) pos = add(out, pos, yRef);
        return pos;
    }

    public Node getParent() { return parent; }

    public boolean getTransformTo(Node target, Transform transform) {
        if (target == null || transform == null) throw new NullPointerException();
        if (root() != target.root()) return false;
        float[] a = new float[16], b = new float[16];
        worldMatrix(a);
        target.worldMatrix(b);
        if (!Transform.invert(b, b)) throw new ArithmeticException();
        Transform.mul(b, a, transform.m);
        return true;
    }

    Node root() {
        Node n = this;
        while (n.parent != null) n = n.parent;
        return n;
    }

    /** Local-to-root transform. */
    void worldMatrix(float[] out) {
        composite(out);
        float[] tmp = new float[16];
        for (Node p = parent; p != null; p = p.parent) {
            p.composite(tmp);
            Transform.mul(tmp, out, out);
        }
    }

    public void setAlignment(Node zRef, int zTarget, Node yRef, int yTarget) {
        if (zTarget < NONE || zTarget > Z_AXIS || yTarget < NONE || yTarget > Z_AXIS) throw new IllegalArgumentException();
        if (zRef == this || yRef == this) throw new IllegalArgumentException();
        if (zRef != null && zRef == yRef && zTarget == yTarget && zTarget != NONE) throw new IllegalArgumentException();
        this.zRef = zRef;
        this.zTarget = zTarget;
        this.yRef = yRef;
        this.yTarget = yTarget;
    }

    public Node getAlignmentReference(int axis) {
        if (axis == Z_AXIS) return zRef;
        if (axis == Y_AXIS) return yRef;
        throw new IllegalArgumentException();
    }

    public int getAlignmentTarget(int axis) {
        if (axis == Z_AXIS) return zTarget;
        if (axis == Y_AXIS) return yTarget;
        throw new IllegalArgumentException();
    }

    public final void align(Node reference) {
        alignTree(reference);
    }

    void alignTree(Node reference) { alignSelf(reference); }

    /** Orients this node so its Z (and Y) axes point at the alignment targets. */
    void alignSelf(Node reference) {
        if (zTarget == NONE && yTarget == NONE) return;
        Node zr = zRef != null ? zRef : reference, yr = yRef != null ? yRef : reference;
        if (zr == null && zTarget != NONE || yr == null && yTarget != NONE) throw new IllegalStateException("No alignment reference");
        float[] parentWorld = new float[16];
        if (parent != null) parent.worldMatrix(parentWorld);
        else new Transform().get(parentWorld);
        float[] inv = new float[16];
        if (!Transform.invert(parentWorld, inv)) return;
        float[] z = zTarget == NONE ? null : targetDir(zr, zTarget, inv);
        float[] y = yTarget == NONE ? null : targetDir(yr, yTarget, inv);
        if (z == null && y == null) return;
        float[] xa = new float[3], ya = new float[3], za = new float[3];
        if (z != null) {
            norm(z, za);
            float[] up = y != null ? y : new float[] {0, 1, 0};
            cross(up, za, xa);
            if (len(xa) < 1e-6f) cross(new float[] {1, 0, 0}, za, xa);
            norm(xa, xa);
            cross(za, xa, ya);
        } else {
            norm(y, ya);
            cross(ya, new float[] {0, 0, 1}, xa);
            if (len(xa) < 1e-6f) cross(ya, new float[] {1, 0, 0}, xa);
            norm(xa, xa);
            cross(xa, ya, za);
        }
        /* rotation matrix columns are the new axes -> quaternion */
        float m00 = xa[0], m01 = ya[0], m02 = za[0], m10 = xa[1], m11 = ya[1], m12 = za[1], m20 = xa[2], m21 = ya[2], m22 = za[2];
        float tr = m00 + m11 + m22;
        if (tr > 0) {
            float s = (float) Math.sqrt(tr + 1) * 2;
            q[3] = 0.25f * s;
            q[0] = (m21 - m12) / s;
            q[1] = (m02 - m20) / s;
            q[2] = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            float s = (float) Math.sqrt(1 + m00 - m11 - m22) * 2;
            q[3] = (m21 - m12) / s;
            q[0] = 0.25f * s;
            q[1] = (m01 + m10) / s;
            q[2] = (m02 + m20) / s;
        } else if (m11 > m22) {
            float s = (float) Math.sqrt(1 + m11 - m00 - m22) * 2;
            q[3] = (m02 - m20) / s;
            q[0] = (m01 + m10) / s;
            q[1] = 0.25f * s;
            q[2] = (m12 + m21) / s;
        } else {
            float s = (float) Math.sqrt(1 + m22 - m00 - m11) * 2;
            q[3] = (m10 - m01) / s;
            q[0] = (m02 + m20) / s;
            q[1] = (m12 + m21) / s;
            q[2] = 0.25f * s;
        }
        Quat.normalize(q);
    }

    /** Direction (in parent space) from this node's origin towards the target of ref. */
    private float[] targetDir(Node ref, int target, float[] parentInv) {
        float[] w = new float[16];
        ref.worldMatrix(w);
        float[] p = new float[4];
        float[] d = new float[3];
        if (target == ORIGIN) {
            float[] o = {w[3], w[7], w[11], 1};
            mulv(parentInv, o, p);
            d[0] = p[0] - tx;
            d[1] = p[1] - ty;
            d[2] = p[2] - tz;
        } else {
            int c = target == X_AXIS ? 0 : (target == Y_AXIS ? 1 : 2);
            float[] ax = {w[c], w[4 + c], w[8 + c], 0};
            mulv(parentInv, ax, p);
            d[0] = p[0];
            d[1] = p[1];
            d[2] = p[2];
        }
        return len(d) < 1e-9f ? null : d;
    }

    private static void mulv(float[] m, float[] v, float[] o) {
        for (int r = 0; r < 4; r++) o[r] = m[r * 4] * v[0] + m[r * 4 + 1] * v[1] + m[r * 4 + 2] * v[2] + m[r * 4 + 3] * v[3];
    }

    static float len(float[] v) { return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); }

    static void norm(float[] v, float[] o) {
        float l = len(v);
        if (l == 0) l = 1;
        o[0] = v[0] / l;
        o[1] = v[1] / l;
        o[2] = v[2] / l;
    }

    static void cross(float[] a, float[] b, float[] o) {
        float x = a[1] * b[2] - a[2] * b[1], y = a[2] * b[0] - a[0] * b[2], z = a[0] * b[1] - a[1] * b[0];
        o[0] = x;
        o[1] = y;
        o[2] = z;
    }

    public void setAlphaFactor(float alphaFactor) {
        if (alphaFactor < 0 || alphaFactor > 1) throw new IllegalArgumentException();
        this.alphaFactor = alphaFactor;
    }

    public float getAlphaFactor() { return alphaFactor; }

    public void setRenderingEnable(boolean enable) { rendering = enable; }

    public boolean isRenderingEnabled() { return rendering; }

    public void setPickingEnable(boolean enable) { picking = enable; }

    public boolean isPickingEnabled() { return picking; }

    public void setScope(int scope) { this.scope = scope; }

    public int getScope() { return scope; }

    boolean acceptsProperty(int p) {
        return p == AnimationTrack.ALPHA || p == AnimationTrack.PICKABILITY || p == AnimationTrack.VISIBILITY || super.acceptsProperty(p);
    }

    void applyAnimation(int property, float[] v, int comps) {
        switch (property) {
            case AnimationTrack.ALPHA:
                alphaFactor = Math.max(0, Math.min(1, v[0]));
                break;
            case AnimationTrack.PICKABILITY:
                picking = v[0] >= 0.5f;
                break;
            case AnimationTrack.VISIBILITY:
                rendering = v[0] >= 0.5f;
                break;
            default:
                super.applyAnimation(property, v, comps);
        }
    }
}
