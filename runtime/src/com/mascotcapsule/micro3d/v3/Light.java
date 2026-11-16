package com.mascotcapsule.micro3d.v3;

public class Light {
    private final Vector3D direction = new Vector3D(0, 0, 4096);
    private int dirIntensity = 4096;
    private int ambIntensity;

    public Light() {}

    public Light(Vector3D dir, int dirIntensity, int ambIntensity) {
        if (dir == null) throw new NullPointerException();
        direction.set(dir);
        this.dirIntensity = dirIntensity;
        this.ambIntensity = ambIntensity;
    }

    public final int getAmbientIntensity() { return ambIntensity; }

    public final void setAmbientIntensity(int p) { ambIntensity = p; }

    public final Vector3D getParallelLightDirection() { return direction; }

    public final void setParallelLightDirection(Vector3D v) {
        if (v == null) throw new NullPointerException();
        direction.set(v);
    }

    public final int getParallelLightIntensity() { return dirIntensity; }

    public final void setParallelLightIntensity(int p) { dirIntensity = p; }

    /* deprecated aliases */
    public final int getAmbIntensity() { return ambIntensity; }

    public final void setAmbIntensity(int p) { ambIntensity = p; }

    public Vector3D getDirection() { return direction; }

    public final void setDirection(Vector3D v) { setParallelLightDirection(v); }

    public final int getDirIntensity() { return dirIntensity; }

    public final void setDirIntensity(int p) { dirIntensity = p; }
}
