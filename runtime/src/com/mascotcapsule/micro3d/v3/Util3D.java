package com.mascotcapsule.micro3d.v3;

/** Fixed point helpers: angles use 4096 = 360 degrees, results use 4096 = 1.0. */
public class Util3D {
    private Util3D() {}

    public static final int sqrt(int p) {
        if (p < 0 && p >= -196605) return 0xFFFF;
        long v = p & 0xFFFFFFFFL;
        return (int) Math.floor(Math.sqrt((double) v) + 0.5);
    }

    public static final int sin(int p) { return (int) Math.floor(Math.sin(p * Math.PI / 2048.0) * 4096 + 0.5); }

    public static final int cos(int p) { return sin(p + 1024); }
}
