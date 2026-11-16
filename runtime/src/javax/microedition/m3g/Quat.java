package javax.microedition.m3g;

/** Quaternion helpers (x, y, z, w). */
final class Quat {
    private Quat() {}

    static void fromAxisAngle(float angleDeg, float ax, float ay, float az, float[] q) {
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (angleDeg == 0 || len == 0) {
            q[0] = q[1] = q[2] = 0;
            q[3] = 1;
            return;
        }
        double half = Math.toRadians(angleDeg) / 2;
        float s = (float) Math.sin(half) / len;
        q[0] = ax * s;
        q[1] = ay * s;
        q[2] = az * s;
        q[3] = (float) Math.cos(half);
    }

    static void toAxisAngle(float[] q, float[] out) {
        float x = q[0], y = q[1], z = q[2], w = q[3];
        float len = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (len > 0) {
            x /= len;
            y /= len;
            z /= len;
            w /= len;
        }
        if (w > 1) w = 1;
        if (w < -1) w = -1;
        float s = (float) Math.sqrt(1 - w * w);
        float angle = (float) Math.toDegrees(2 * Math.acos(w));
        if (s < 1e-6f || angle == 0) {
            out[0] = 0;
            out[1] = 0;
            out[2] = 0;
            out[3] = 0;
            return;
        }
        out[0] = angle;
        out[1] = x / s;
        out[2] = y / s;
        out[3] = z / s;
    }

    /** r = a * b */
    static void mul(float[] a, float[] b, float[] r) {
        float x = a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1];
        float y = a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0];
        float z = a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3];
        float w = a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2];
        r[0] = x;
        r[1] = y;
        r[2] = z;
        r[3] = w;
    }

    static void normalize(float[] q) {
        float len = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (len == 0) {
            q[0] = q[1] = q[2] = 0;
            q[3] = 1;
            return;
        }
        for (int i = 0; i < 4; i++) q[i] /= len;
    }

    static void toMatrix(float x, float y, float z, float w, float[] m) {
        float xx = x * x, yy = y * y, zz = z * z, xy = x * y, xz = x * z, yz = y * z, wx = w * x, wy = w * y, wz = w * z;
        m[0] = 1 - 2 * (yy + zz);
        m[1] = 2 * (xy - wz);
        m[2] = 2 * (xz + wy);
        m[3] = 0;
        m[4] = 2 * (xy + wz);
        m[5] = 1 - 2 * (xx + zz);
        m[6] = 2 * (yz - wx);
        m[7] = 0;
        m[8] = 2 * (xz - wy);
        m[9] = 2 * (yz + wx);
        m[10] = 1 - 2 * (xx + yy);
        m[11] = 0;
        m[12] = m[13] = m[14] = 0;
        m[15] = 1;
    }

    static void slerp(float[] a, int ao, float[] b, int bo, float t, float[] out) {
        float ax = a[ao], ay = a[ao + 1], az = a[ao + 2], aw = a[ao + 3];
        float bx = b[bo], by = b[bo + 1], bz = b[bo + 2], bw = b[bo + 3];
        float cos = ax * bx + ay * by + az * bz + aw * bw;
        if (cos < 0) {
            cos = -cos;
            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
        }
        float k0, k1;
        if (cos > 0.9995f) {
            k0 = 1 - t;
            k1 = t;
        } else {
            double ang = Math.acos(cos);
            double s = Math.sin(ang);
            k0 = (float) (Math.sin((1 - t) * ang) / s);
            k1 = (float) (Math.sin(t * ang) / s);
        }
        out[0] = ax * k0 + bx * k1;
        out[1] = ay * k0 + by * k1;
        out[2] = az * k0 + bz * k1;
        out[3] = aw * k0 + bw * k1;
        normalize(out);
    }
}
