package javax.microedition.m3g;

public class TriangleStripArray extends IndexBuffer {
    private final int[] tris;

    public TriangleStripArray(int firstIndex, int[] stripLengths) {
        this(null, firstIndex, stripLengths);
    }

    public TriangleStripArray(int[] indices, int[] stripLengths) {
        this(indices, 0, stripLengths);
        if (indices == null) throw new NullPointerException();
    }

    private TriangleStripArray(int[] indices, int first, int[] lengths) {
        if (lengths == null) throw new NullPointerException();
        if (lengths.length == 0) throw new IllegalArgumentException();
        int total = 0, ntri = 0;
        for (int i = 0; i < lengths.length; i++) {
            if (lengths[i] < 3) throw new IllegalArgumentException("Strip length < 3");
            total += lengths[i];
            ntri += lengths[i] - 2;
        }
        if (indices != null && indices.length < total) throw new IllegalArgumentException();
        if (indices == null && (first < 0 || first + total > 65536)) throw new IndexOutOfBoundsException();
        tris = new int[ntri * 3];
        int k = 0, base = 0;
        for (int s = 0; s < lengths.length; s++) {
            for (int i = 0; i + 2 < lengths[s]; i++) {
                int a = idx(indices, first, base + i), b = idx(indices, first, base + i + 1), c = idx(indices, first, base + i + 2);
                if ((i & 1) == 0) {
                    tris[k++] = a;
                    tris[k++] = b;
                } else {
                    tris[k++] = b;
                    tris[k++] = a;
                }
                tris[k++] = c;
            }
            base += lengths[s];
        }
    }

    private TriangleStripArray(int[] tris) { this.tris = tris; }

    private static int idx(int[] indices, int first, int i) { return indices != null ? indices[i] : first + i; }

    Object3D dup() { return new TriangleStripArray(tris); }

    int[] triangles() { return tris; }
}
