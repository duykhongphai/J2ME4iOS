package javax.microedition.m3g;

public class RayIntersection {
    private Node node;
    private float distance;
    private final float[] ray = {0, 0, 0, 0, 0, 1};
    private final float[] normal = {0, 0, 1};
    private int submesh;
    private final float[] s = new float[2], t = new float[2];

    public RayIntersection() {}

    void set(Node n, float dist, float[] r, float[] nrm, int sub, float[] ts, float[] tt) {
        node = n;
        distance = dist;
        System.arraycopy(r, 0, ray, 0, 6);
        System.arraycopy(nrm, 0, normal, 0, 3);
        submesh = sub;
        System.arraycopy(ts, 0, s, 0, 2);
        System.arraycopy(tt, 0, t, 0, 2);
    }

    public Node getIntersected() { return node; }

    public float getDistance() { return distance; }

    public int getSubmeshIndex() { return submesh; }

    public float getTextureS(int index) {
        if (index < 0 || index >= 2) throw new IndexOutOfBoundsException();
        return s[index];
    }

    public float getTextureT(int index) {
        if (index < 0 || index >= 2) throw new IndexOutOfBoundsException();
        return t[index];
    }

    public float getNormalX() { return normal[0]; }

    public float getNormalY() { return normal[1]; }

    public float getNormalZ() { return normal[2]; }

    public void getRay(float[] ray) {
        if (ray == null) throw new NullPointerException();
        if (ray.length < 6) throw new IllegalArgumentException();
        System.arraycopy(this.ray, 0, ray, 0, 6);
    }
}
