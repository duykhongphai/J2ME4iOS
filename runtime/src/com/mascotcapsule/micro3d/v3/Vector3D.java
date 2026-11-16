package com.mascotcapsule.micro3d.v3;

public class Vector3D {
    public int x, y, z;

    public Vector3D() {}

    public Vector3D(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3D(Vector3D v) {
        if (v == null) throw new NullPointerException();
        x = v.x;
        y = v.y;
        z = v.z;
    }

    public final int getX() { return x; }

    public final int getY() { return y; }

    public final int getZ() { return z; }

    public final void setX(int x) { this.x = x; }

    public final void setY(int y) { this.y = y; }

    public final void setZ(int z) { this.z = z; }

    public final void set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public final void set(Vector3D v) {
        if (v == null) throw new NullPointerException();
        x = v.x;
        y = v.y;
        z = v.z;
    }

    public final int innerProduct(Vector3D v) {
        if (v == null) throw new NullPointerException();
        return x * v.x + y * v.y + z * v.z;
    }

    public static final int innerProduct(Vector3D v1, Vector3D v2) {
        if (v1 == null || v2 == null) throw new NullPointerException();
        return v1.innerProduct(v2);
    }

    public final void outerProduct(Vector3D v) {
        if (v == null) throw new NullPointerException();
        int nx = y * v.z - z * v.y, ny = z * v.x - x * v.z, nz = x * v.y - y * v.x;
        x = nx;
        y = ny;
        z = nz;
    }

    public static final Vector3D outerProduct(Vector3D v1, Vector3D v2) {
        if (v1 == null || v2 == null) throw new NullPointerException();
        Vector3D r = new Vector3D(v1);
        r.outerProduct(v2);
        return r;
    }

    public final void unit() {
        double len = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
        if (len == 0) {
            x = 0;
            y = 0;
            z = 4096;
            return;
        }
        x = (int) (x * 4096.0 / len);
        y = (int) (y * 4096.0 / len);
        z = (int) (z * 4096.0 / len);
    }
}
