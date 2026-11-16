package javax.microedition.m3g;

public class Camera extends Node {
    public static final int GENERIC = 48;
    public static final int PARALLEL = 49;
    public static final int PERSPECTIVE = 50;

    int projection = GENERIC;
    float fovy, aspect, near, far;
    final Transform generic = new Transform();

    public Camera() {}

    Object3D dup() {
        Camera c = new Camera();
        copyNodeTo(c);
        c.projection = projection;
        c.fovy = fovy;
        c.aspect = aspect;
        c.near = near;
        c.far = far;
        c.generic.set(generic);
        return c;
    }

    public void setParallel(float fovy, float aspectRatio, float near, float far) {
        if (fovy <= 0 || aspectRatio <= 0 || near == far) throw new IllegalArgumentException();
        projection = PARALLEL;
        this.fovy = fovy;
        aspect = aspectRatio;
        this.near = near;
        this.far = far;
    }

    public void setPerspective(float fovy, float aspectRatio, float near, float far) {
        if (fovy <= 0 || fovy >= 180 || aspectRatio <= 0 || near <= 0 || far <= 0 || near == far) throw new IllegalArgumentException();
        projection = PERSPECTIVE;
        this.fovy = fovy;
        aspect = aspectRatio;
        this.near = near;
        this.far = far;
    }

    public void setGeneric(Transform transform) {
        if (transform == null) throw new NullPointerException();
        projection = GENERIC;
        generic.set(transform);
    }

    public int getProjection(Transform transform) {
        if (transform != null) projectionMatrix(transform.m, aspect > 0 ? aspect : 1);
        return projection;
    }

    public int getProjection(float[] params) {
        if (params != null) {
            if (params.length < 4) throw new IllegalArgumentException();
            params[0] = fovy;
            params[1] = aspect;
            params[2] = near;
            params[3] = far;
        }
        return projection;
    }

    /** Projection matrix (row-major). viewportAspect is unused: M3G uses the camera's own aspect ratio. */
    void projectionMatrix(float[] m, float viewportAspect) {
        for (int i = 0; i < 16; i++) m[i] = 0;
        if (projection == GENERIC) {
            System.arraycopy(generic.m, 0, m, 0, 16);
        } else if (projection == PERSPECTIVE) {
            float f = (float) (1.0 / Math.tan(Math.toRadians(fovy) / 2));
            m[0] = f / aspect;
            m[5] = f;
            m[10] = (near + far) / (near - far);
            m[11] = 2 * near * far / (near - far);
            m[14] = -1;
        } else {
            float h = fovy, w = aspect * fovy;
            m[0] = 2 / w;
            m[5] = 2 / h;
            m[10] = -2 / (far - near);
            m[11] = -(far + near) / (far - near);
            m[15] = 1;
        }
    }

    boolean acceptsProperty(int p) {
        return p == AnimationTrack.FAR_DISTANCE || p == AnimationTrack.FIELD_OF_VIEW || p == AnimationTrack.NEAR_DISTANCE
            || super.acceptsProperty(p);
    }

    void applyAnimation(int property, float[] v, int comps) {
        switch (property) {
            case AnimationTrack.FAR_DISTANCE:
                far = v[0];
                break;
            case AnimationTrack.FIELD_OF_VIEW:
                fovy = v[0];
                break;
            case AnimationTrack.NEAR_DISTANCE:
                near = v[0];
                break;
            default:
                super.applyAnimation(property, v, comps);
        }
    }
}
