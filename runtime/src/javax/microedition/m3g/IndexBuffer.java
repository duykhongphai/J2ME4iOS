package javax.microedition.m3g;

public abstract class IndexBuffer extends Object3D {
    IndexBuffer() {}

    /** Triangle list (3 indices per triangle). */
    abstract int[] triangles();

    public int getIndexCount() { return triangles().length; }

    public void getIndices(int[] indices) {
        if (indices == null) throw new NullPointerException();
        int[] t = triangles();
        if (indices.length < t.length) throw new IllegalArgumentException();
        System.arraycopy(t, 0, indices, 0, t.length);
    }
}
