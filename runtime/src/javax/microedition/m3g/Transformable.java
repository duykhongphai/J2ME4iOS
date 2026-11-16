package javax.microedition.m3g;

public abstract class Transformable extends Object3D {
    float tx, ty, tz;
    float sx = 1, sy = 1, sz = 1;
    final float[] q = {0, 0, 0, 1};
    Transform general; /* null = identity */

    Transformable() {}

    void copyTransformTo(Transformable o) {
        o.tx = tx;
        o.ty = ty;
        o.tz = tz;
        o.sx = sx;
        o.sy = sy;
        o.sz = sz;
        System.arraycopy(q, 0, o.q, 0, 4);
        o.general = general == null ? null : new Transform(general);
    }

    public void setOrientation(float angle, float ax, float ay, float az) {
        if (angle != 0 && ax == 0 && ay == 0 && az == 0) throw new IllegalArgumentException();
        Quat.fromAxisAngle(angle, ax, ay, az, q);
    }

    public void preRotate(float angle, float ax, float ay, float az) {
        if (angle != 0 && ax == 0 && ay == 0 && az == 0) throw new IllegalArgumentException();
        float[] r = new float[4];
        Quat.fromAxisAngle(angle, ax, ay, az, r);
        Quat.mul(r, q, q);
        Quat.normalize(q);
    }

    public void postRotate(float angle, float ax, float ay, float az) {
        if (angle != 0 && ax == 0 && ay == 0 && az == 0) throw new IllegalArgumentException();
        float[] r = new float[4];
        Quat.fromAxisAngle(angle, ax, ay, az, r);
        Quat.mul(q, r, q);
        Quat.normalize(q);
    }

    public void getOrientation(float[] angleAxis) {
        if (angleAxis == null) throw new NullPointerException();
        if (angleAxis.length < 4) throw new IllegalArgumentException();
        Quat.toAxisAngle(q, angleAxis);
    }

    public void setScale(float sx, float sy, float sz) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
    }

    public void scale(float sx, float sy, float sz) {
        this.sx *= sx;
        this.sy *= sy;
        this.sz *= sz;
    }

    public void getScale(float[] xyz) {
        if (xyz == null) throw new NullPointerException();
        if (xyz.length < 3) throw new IllegalArgumentException();
        xyz[0] = sx;
        xyz[1] = sy;
        xyz[2] = sz;
    }

    public void setTranslation(float tx, float ty, float tz) {
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
    }

    public void translate(float tx, float ty, float tz) {
        this.tx += tx;
        this.ty += ty;
        this.tz += tz;
    }

    public void getTranslation(float[] xyz) {
        if (xyz == null) throw new NullPointerException();
        if (xyz.length < 3) throw new IllegalArgumentException();
        xyz[0] = tx;
        xyz[1] = ty;
        xyz[2] = tz;
    }

    public void setTransform(Transform transform) {
        if (transform == null) general = null;
        else {
            if (this instanceof Node && !(this instanceof Texture2D)) {
                float[] m = transform.m;
                if (m[12] != 0 || m[13] != 0 || m[14] != 0 || m[15] != 1)
                    throw new IllegalArgumentException("Bottom row must be 0 0 0 1 for nodes");
            }
            general = new Transform(transform);
        }
    }

    public void getTransform(Transform transform) {
        if (transform == null) throw new NullPointerException();
        if (general == null) transform.setIdentity();
        else transform.set(general);
    }

    public void getCompositeTransform(Transform transform) {
        if (transform == null) throw new NullPointerException();
        composite(transform.m);
    }

    /** T * R * S * M */
    void composite(float[] out) {
        float[] r = new float[16];
        Quat.toMatrix(q[0], q[1], q[2], q[3], r);
        for (int row = 0; row < 3; row++) {
            out[row * 4] = r[row * 4] * sx;
            out[row * 4 + 1] = r[row * 4 + 1] * sy;
            out[row * 4 + 2] = r[row * 4 + 2] * sz;
        }
        out[3] = tx;
        out[7] = ty;
        out[11] = tz;
        out[12] = out[13] = out[14] = 0;
        out[15] = 1;
        if (general != null) Transform.mul(out, general.m, out);
    }

    boolean acceptsProperty(int p) {
        return p == AnimationTrack.ORIENTATION || p == AnimationTrack.SCALE || p == AnimationTrack.TRANSLATION || super.acceptsProperty(p);
    }

    void applyAnimation(int property, float[] v, int comps) {
        switch (property) {
            case AnimationTrack.ORIENTATION:
                System.arraycopy(v, 0, q, 0, 4);
                Quat.normalize(q);
                break;
            case AnimationTrack.SCALE:
                if (comps >= 3) {
                    sx = v[0];
                    sy = v[1];
                    sz = v[2];
                } else sx = sy = sz = v[0];
                break;
            case AnimationTrack.TRANSLATION:
                tx = v[0];
                ty = v[1];
                tz = comps > 2 ? v[2] : 0;
                break;
            default:
                super.applyAnimation(property, v, comps);
        }
    }
}
