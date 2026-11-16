package com.mascotcapsule.micro3d.v3;

/** 3x4 affine matrix: rotation/scale part uses 4096 = 1.0, translation (m03, m13, m23) is raw. */
public class AffineTrans {
    public int m00, m01, m02, m03;
    public int m10, m11, m12, m13;
    public int m20, m21, m22, m23;

    public AffineTrans() {}

    public AffineTrans(AffineTrans a) { set(a); }

    public AffineTrans(int[] a) { set(a); }

    public AffineTrans(int[][] a) { set(a); }

    public AffineTrans(int[] a, int offset) { set(a, offset); }

    public AffineTrans(int m00, int m01, int m02, int m03, int m10, int m11, int m12, int m13, int m20, int m21, int m22, int m23) {
        set(m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23);
    }

    public final void get(int[] a) { get(a, 0); }

    public final void get(int[] a, int offset) {
        if (a == null) throw new NullPointerException();
        if (offset < 0 || a.length - offset < 12) throw new IllegalArgumentException();
        a[offset] = m00;
        a[offset + 1] = m01;
        a[offset + 2] = m02;
        a[offset + 3] = m03;
        a[offset + 4] = m10;
        a[offset + 5] = m11;
        a[offset + 6] = m12;
        a[offset + 7] = m13;
        a[offset + 8] = m20;
        a[offset + 9] = m21;
        a[offset + 10] = m22;
        a[offset + 11] = m23;
    }

    public final void set(AffineTrans a) {
        if (a == null) throw new NullPointerException();
        set(a.m00, a.m01, a.m02, a.m03, a.m10, a.m11, a.m12, a.m13, a.m20, a.m21, a.m22, a.m23);
    }

    public final void set(int[] a) { set(a, 0); }

    public final void set(int[] a, int offset) {
        if (a == null) throw new NullPointerException();
        if (offset < 0 || a.length - offset < 12) throw new IllegalArgumentException();
        set(a[offset], a[offset + 1], a[offset + 2], a[offset + 3], a[offset + 4], a[offset + 5], a[offset + 6], a[offset + 7],
            a[offset + 8], a[offset + 9], a[offset + 10], a[offset + 11]);
    }

    public final void set(int[][] a) {
        if (a == null) throw new NullPointerException();
        if (a.length < 3 || a[0].length < 4 || a[1].length < 4 || a[2].length < 4) throw new IllegalArgumentException();
        set(a[0][0], a[0][1], a[0][2], a[0][3], a[1][0], a[1][1], a[1][2], a[1][3], a[2][0], a[2][1], a[2][2], a[2][3]);
    }

    public final void set(int m00, int m01, int m02, int m03, int m10, int m11, int m12, int m13, int m20, int m21, int m22, int m23) {
        this.m00 = m00;
        this.m01 = m01;
        this.m02 = m02;
        this.m03 = m03;
        this.m10 = m10;
        this.m11 = m11;
        this.m12 = m12;
        this.m13 = m13;
        this.m20 = m20;
        this.m21 = m21;
        this.m22 = m22;
        this.m23 = m23;
    }

    public final void setIdentity() { set(4096, 0, 0, 0, 0, 4096, 0, 0, 0, 0, 4096, 0); }

    public final void lookAt(Vector3D pos, Vector3D look, Vector3D up) {
        if (pos == null || look == null || up == null) throw new NullPointerException();
        Vector3D r0 = Vector3D.outerProduct(look, up);
        r0.unit();
        Vector3D r1 = Vector3D.outerProduct(look, r0);
        r1.unit();
        Vector3D r2 = new Vector3D(look);
        r2.unit();
        m00 = r0.x;
        m01 = r0.y;
        m02 = r0.z;
        m10 = r1.x;
        m11 = r1.y;
        m12 = r1.z;
        m20 = r2.x;
        m21 = r2.y;
        m22 = r2.z;
        m03 = (-pos.innerProduct(r0) + 2048) >> 12;
        m13 = (-pos.innerProduct(r1) + 2048) >> 12;
        m23 = (-pos.innerProduct(r2) + 2048) >> 12;
    }

    public final void mul(AffineTrans a) {
        if (a == null) throw new NullPointerException();
        mul(this, a);
    }

    public final void mul(AffineTrans a1, AffineTrans a2) {
        if (a1 == null || a2 == null) throw new NullPointerException();
        int n00 = r(a1.m00 * a2.m00 + a1.m01 * a2.m10 + a1.m02 * a2.m20);
        int n01 = r(a1.m00 * a2.m01 + a1.m01 * a2.m11 + a1.m02 * a2.m21);
        int n02 = r(a1.m00 * a2.m02 + a1.m01 * a2.m12 + a1.m02 * a2.m22);
        int n03 = r(a1.m00 * a2.m03 + a1.m01 * a2.m13 + a1.m02 * a2.m23) + a1.m03;
        int n10 = r(a1.m10 * a2.m00 + a1.m11 * a2.m10 + a1.m12 * a2.m20);
        int n11 = r(a1.m10 * a2.m01 + a1.m11 * a2.m11 + a1.m12 * a2.m21);
        int n12 = r(a1.m10 * a2.m02 + a1.m11 * a2.m12 + a1.m12 * a2.m22);
        int n13 = r(a1.m10 * a2.m03 + a1.m11 * a2.m13 + a1.m12 * a2.m23) + a1.m13;
        int n20 = r(a1.m20 * a2.m00 + a1.m21 * a2.m10 + a1.m22 * a2.m20);
        int n21 = r(a1.m20 * a2.m01 + a1.m21 * a2.m11 + a1.m22 * a2.m21);
        int n22 = r(a1.m20 * a2.m02 + a1.m21 * a2.m12 + a1.m22 * a2.m22);
        int n23 = r(a1.m20 * a2.m03 + a1.m21 * a2.m13 + a1.m22 * a2.m23) + a1.m23;
        set(n00, n01, n02, n03, n10, n11, n12, n13, n20, n21, n22, n23);
    }

    private static int r(int v) { return (v + 2048) >> 12; }

    public final void rotationX(int r) {
        int s = Util3D.sin(r), c = Util3D.cos(r);
        m00 = 4096;
        m01 = 0;
        m02 = 0;
        m10 = 0;
        m11 = c;
        m12 = -s;
        m20 = 0;
        m21 = s;
        m22 = c;
    }

    public final void rotationY(int r) {
        int s = Util3D.sin(r), c = Util3D.cos(r);
        m00 = c;
        m01 = 0;
        m02 = s;
        m10 = 0;
        m11 = 4096;
        m12 = 0;
        m20 = -s;
        m21 = 0;
        m22 = c;
    }

    public final void rotationZ(int r) {
        int s = Util3D.sin(r), c = Util3D.cos(r);
        m00 = c;
        m01 = -s;
        m02 = 0;
        m10 = s;
        m11 = c;
        m12 = 0;
        m20 = 0;
        m21 = 0;
        m22 = 4096;
    }

    public final void setRotation(Vector3D v, int r) {
        if (v == null) throw new NullPointerException();
        int s = Util3D.sin(r), c = Util3D.cos(r), t = 4096 - c;
        int x = v.x, y = v.y, z = v.z;
        int xx = (x * x + 2048) >> 12, yy = (y * y + 2048) >> 12, zz = (z * z + 2048) >> 12;
        int xy = (x * y + 2048) >> 12, xz = (x * z + 2048) >> 12, yz = (y * z + 2048) >> 12;
        int xs = (x * s + 2048) >> 12, ys = (y * s + 2048) >> 12, zs = (z * s + 2048) >> 12;
        m00 = c + ((xx * t + 2048) >> 12);
        m01 = ((xy * t + 2048) >> 12) - zs;
        m02 = ((xz * t + 2048) >> 12) + ys;
        m10 = ((xy * t + 2048) >> 12) + zs;
        m11 = c + ((yy * t + 2048) >> 12);
        m12 = ((yz * t + 2048) >> 12) - xs;
        m20 = ((xz * t + 2048) >> 12) - ys;
        m21 = ((yz * t + 2048) >> 12) + xs;
        m22 = c + ((zz * t + 2048) >> 12);
    }

    public final Vector3D transform(Vector3D v) {
        if (v == null) throw new NullPointerException();
        return new Vector3D(r(m00 * v.x + m01 * v.y + m02 * v.z) + m03, r(m10 * v.x + m11 * v.y + m12 * v.z) + m13,
                            r(m20 * v.x + m21 * v.y + m22 * v.z) + m23);
    }

    /* deprecated aliases */
    public final void multiply(AffineTrans a) { mul(a); }

    public final void multiply(AffineTrans a1, AffineTrans a2) { mul(a1, a2); }

    public final void rotationV(Vector3D v, int r) { setRotation(v, r); }

    public final void setRotationX(int r) { rotationX(r); }

    public final void setRotationY(int r) { rotationY(r); }

    public final void setRotationZ(int r) { rotationZ(r); }

    public final void setViewTrans(Vector3D pos, Vector3D look, Vector3D up) { lookAt(pos, look, up); }

    public final Vector3D transPoint(Vector3D v) { return transform(v); }
}
