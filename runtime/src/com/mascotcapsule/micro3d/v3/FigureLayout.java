package com.mascotcapsule.micro3d.v3;

public class FigureLayout {
    static final int PARALLEL_SCALE = 0, PARALLEL_SIZE = 1, PERSPECTIVE_FOV = 2, PERSPECTIVE_WH = 3;

    AffineTrans[] affineArray;
    int affineIndex;
    int centerX, centerY;
    int scaleX = 512, scaleY = 512;
    int parallelW, parallelH;
    int near, far, angle, perspW, perspH;
    int mode = PARALLEL_SCALE;

    public FigureLayout() { this(null, 512, 512, 0, 0); }

    public FigureLayout(AffineTrans trans, int sx, int sy, int cx, int cy) {
        setAffineTrans(trans);
        setScale(sx, sy);
        setCenter(cx, cy);
    }

    AffineTrans affine() {
        if (affineArray == null || affineArray.length == 0) return null;
        int i = affineIndex < 0 || affineIndex >= affineArray.length ? 0 : affineIndex;
        return affineArray[i];
    }

    public final AffineTrans getAffineTrans() { return affine(); }

    public final void setAffineTrans(AffineTrans trans) {
        if (trans == null) {
            trans = new AffineTrans();
            trans.setIdentity();
        }
        if (affineArray == null || affineArray.length == 0) affineArray = new AffineTrans[1];
        affineArray[0] = trans;
        affineIndex = 0;
    }

    public final void setAffineTrans(AffineTrans[] trans) {
        if (trans == null) throw new NullPointerException();
        for (int i = 0; i < trans.length; i++)
            if (trans[i] == null) throw new NullPointerException();
        affineArray = trans;
        affineIndex = 0;
    }

    public final void setAffineTransArray(AffineTrans[] trans) { setAffineTrans(trans); }

    public final void selectAffineTrans(int idx) {
        if (affineArray == null || idx < 0 || idx >= affineArray.length) throw new IllegalArgumentException();
        affineIndex = idx;
    }

    public final int getCenterX() { return centerX; }

    public final int getCenterY() { return centerY; }

    public final void setCenter(int cx, int cy) {
        centerX = cx;
        centerY = cy;
    }

    public final int getScaleX() { return scaleX; }

    public final int getScaleY() { return scaleY; }

    public final void setScale(int sx, int sy) {
        scaleX = sx;
        scaleY = sy;
        mode = PARALLEL_SCALE;
    }

    public final int getParallelWidth() { return parallelW; }

    public final int getParallelHeight() { return parallelH; }

    public final void setParallelSize(int w, int h) {
        if (w < 0 || h < 0) throw new IllegalArgumentException();
        parallelW = w;
        parallelH = h;
        mode = PARALLEL_SIZE;
    }

    public final void setPerspective(int zNear, int zFar, int angle) {
        if (zNear >= zFar || zNear < 1 || zNear > 32766 || zFar > 32767 || angle < 1 || angle > 2047) throw new IllegalArgumentException();
        near = zNear;
        far = zFar;
        this.angle = angle;
        mode = PERSPECTIVE_FOV;
    }

    public final void setPerspective(int zNear, int zFar, int width, int height) {
        if (zNear >= zFar || zNear < 1 || zNear > 32766 || zFar > 32767 || width < 0 || height < 0) throw new IllegalArgumentException();
        near = zNear;
        far = zFar;
        perspW = width;
        perspH = height;
        mode = PERSPECTIVE_WH;
    }
}
