package javax.microedition.m3g;

/** 4x4 matrix, row-major. */
public class Transform {
    final float[] m = new float[16];

    public Transform() { setIdentity(); }

    public Transform(Transform transform) { set(transform); }

    public void setIdentity() {
        for (int i = 0; i < 16; i++) m[i] = 0;
        m[0] = m[5] = m[10] = m[15] = 1;
    }

    public void set(Transform transform) {
        if (transform == null) throw new NullPointerException();
        System.arraycopy(transform.m, 0, m, 0, 16);
    }

    public void set(float[] matrix) {
        if (matrix == null) throw new NullPointerException();
        if (matrix.length < 16) throw new IllegalArgumentException();
        System.arraycopy(matrix, 0, m, 0, 16);
    }

    public void get(float[] matrix) {
        if (matrix == null) throw new NullPointerException();
        if (matrix.length < 16) throw new IllegalArgumentException();
        System.arraycopy(m, 0, matrix, 0, 16);
    }

    public void invert() {
        if (!invert(m, m)) throw new ArithmeticException("Transform is not invertible");
    }

    /** General 4x4 inverse; returns false if singular. src and dst may alias. */
    static boolean invert(float[] a, float[] out) {
        float[] inv = new float[16];
        inv[0] = a[5] * a[10] * a[15] - a[5] * a[11] * a[14] - a[9] * a[6] * a[15] + a[9] * a[7] * a[14] + a[13] * a[6] * a[11] - a[13] * a[7] * a[10];
        inv[4] = -a[4] * a[10] * a[15] + a[4] * a[11] * a[14] + a[8] * a[6] * a[15] - a[8] * a[7] * a[14] - a[12] * a[6] * a[11] + a[12] * a[7] * a[10];
        inv[8] = a[4] * a[9] * a[15] - a[4] * a[11] * a[13] - a[8] * a[5] * a[15] + a[8] * a[7] * a[13] + a[12] * a[5] * a[11] - a[12] * a[7] * a[9];
        inv[12] = -a[4] * a[9] * a[14] + a[4] * a[10] * a[13] + a[8] * a[5] * a[14] - a[8] * a[6] * a[13] - a[12] * a[5] * a[10] + a[12] * a[6] * a[9];
        inv[1] = -a[1] * a[10] * a[15] + a[1] * a[11] * a[14] + a[9] * a[2] * a[15] - a[9] * a[3] * a[14] - a[13] * a[2] * a[11] + a[13] * a[3] * a[10];
        inv[5] = a[0] * a[10] * a[15] - a[0] * a[11] * a[14] - a[8] * a[2] * a[15] + a[8] * a[3] * a[14] + a[12] * a[2] * a[11] - a[12] * a[3] * a[10];
        inv[9] = -a[0] * a[9] * a[15] + a[0] * a[11] * a[13] + a[8] * a[1] * a[15] - a[8] * a[3] * a[13] - a[12] * a[1] * a[11] + a[12] * a[3] * a[9];
        inv[13] = a[0] * a[9] * a[14] - a[0] * a[10] * a[13] - a[8] * a[1] * a[14] + a[8] * a[2] * a[13] + a[12] * a[1] * a[10] - a[12] * a[2] * a[9];
        inv[2] = a[1] * a[6] * a[15] - a[1] * a[7] * a[14] - a[5] * a[2] * a[15] + a[5] * a[3] * a[14] + a[13] * a[2] * a[7] - a[13] * a[3] * a[6];
        inv[6] = -a[0] * a[6] * a[15] + a[0] * a[7] * a[14] + a[4] * a[2] * a[15] - a[4] * a[3] * a[14] - a[12] * a[2] * a[7] + a[12] * a[3] * a[6];
        inv[10] = a[0] * a[5] * a[15] - a[0] * a[7] * a[13] - a[4] * a[1] * a[15] + a[4] * a[3] * a[13] + a[12] * a[1] * a[7] - a[12] * a[3] * a[5];
        inv[14] = -a[0] * a[5] * a[14] + a[0] * a[6] * a[13] + a[4] * a[1] * a[14] - a[4] * a[2] * a[13] - a[12] * a[1] * a[6] + a[12] * a[2] * a[5];
        inv[3] = -a[1] * a[6] * a[11] + a[1] * a[7] * a[10] + a[5] * a[2] * a[11] - a[5] * a[3] * a[10] - a[9] * a[2] * a[7] + a[9] * a[3] * a[6];
        inv[7] = a[0] * a[6] * a[11] - a[0] * a[7] * a[10] - a[4] * a[2] * a[11] + a[4] * a[3] * a[10] + a[8] * a[2] * a[7] - a[8] * a[3] * a[6];
        inv[11] = -a[0] * a[5] * a[11] + a[0] * a[7] * a[9] + a[4] * a[1] * a[11] - a[4] * a[3] * a[9] - a[8] * a[1] * a[7] + a[8] * a[3] * a[5];
        inv[15] = a[0] * a[5] * a[10] - a[0] * a[6] * a[9] - a[4] * a[1] * a[10] + a[4] * a[2] * a[9] + a[8] * a[1] * a[6] - a[8] * a[2] * a[5];
        float det = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12];
        if (det == 0 || det != det) return false;
        det = 1.0f / det;
        for (int i = 0; i < 16; i++) out[i] = inv[i] * det;
        return true;
    }

    public void transpose() {
        for (int r = 0; r < 4; r++)
            for (int c = r + 1; c < 4; c++) {
                float t = m[r * 4 + c];
                m[r * 4 + c] = m[c * 4 + r];
                m[c * 4 + r] = t;
            }
    }

    /** out = a * b (out may alias a or b). */
    static void mul(float[] a, float[] b, float[] out) {
        float[] r = new float[16];
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                r[i * 4 + j] = a[i * 4] * b[j] + a[i * 4 + 1] * b[4 + j] + a[i * 4 + 2] * b[8 + j] + a[i * 4 + 3] * b[12 + j];
        System.arraycopy(r, 0, out, 0, 16);
    }

    public void postMultiply(Transform transform) {
        if (transform == null) throw new NullPointerException();
        mul(m, transform.m, m);
    }

    void preMultiply(Transform transform) { mul(transform.m, m, m); }

    public void postScale(float sx, float sy, float sz) {
        for (int r = 0; r < 4; r++) {
            m[r * 4] *= sx;
            m[r * 4 + 1] *= sy;
            m[r * 4 + 2] *= sz;
        }
    }

    public void postRotate(float angle, float ax, float ay, float az) {
        if (angle == 0) return;
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len == 0) throw new IllegalArgumentException("Zero rotation axis");
        float[] q = new float[4];
        Quat.fromAxisAngle(angle, ax, ay, az, q);
        postRotateQuat(q[0], q[1], q[2], q[3]);
    }

    public void postRotateQuat(float qx, float qy, float qz, float qw) {
        float len = (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        if (len == 0) throw new IllegalArgumentException("Zero quaternion");
        float[] r = new float[16];
        Quat.toMatrix(qx / len, qy / len, qz / len, qw / len, r);
        mul(m, r, m);
    }

    public void postTranslate(float tx, float ty, float tz) {
        for (int r = 0; r < 4; r++) m[r * 4 + 3] += m[r * 4] * tx + m[r * 4 + 1] * ty + m[r * 4 + 2] * tz;
    }

    public void transform(VertexArray in, float[] out, boolean W) {
        if (in == null || out == null) throw new NullPointerException();
        int n = in.getVertexCount(), cc = in.getComponentCount();
        if (cc == 4) throw new IllegalArgumentException();
        if (out.length < n * 4) throw new IllegalArgumentException();
        short[] d = in.data;
        for (int i = 0; i < n; i++) {
            float x = d[i * cc], y = d[i * cc + 1], z = cc > 2 ? d[i * cc + 2] : 0, w = W ? 1 : 0;
            for (int r = 0; r < 4; r++) out[i * 4 + r] = m[r * 4] * x + m[r * 4 + 1] * y + m[r * 4 + 2] * z + m[r * 4 + 3] * w;
        }
    }

    public void transform(float[] vectors) {
        if (vectors == null) throw new NullPointerException();
        if (vectors.length % 4 != 0) throw new IllegalArgumentException();
        for (int i = 0; i < vectors.length; i += 4) {
            float x = vectors[i], y = vectors[i + 1], z = vectors[i + 2], w = vectors[i + 3];
            for (int r = 0; r < 4; r++) vectors[i + r] = m[r * 4] * x + m[r * 4 + 1] * y + m[r * 4 + 2] * z + m[r * 4 + 3] * w;
        }
    }

    /** this * (x,y,z,w) into out[off..off+3] */
    void apply(float x, float y, float z, float w, float[] out, int off) {
        for (int r = 0; r < 4; r++) out[off + r] = m[r * 4] * x + m[r * 4 + 1] * y + m[r * 4 + 2] * z + m[r * 4 + 3] * w;
    }
}
