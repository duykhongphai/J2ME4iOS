package javax.microedition.m3g;

public class Mesh extends Node {
    VertexBuffer vb;
    IndexBuffer[] submeshes;
    Appearance[] appearances;

    /* deformed geometry for SkinnedMesh / MorphingMesh (null = use vb directly) */
    float[] defPos, defNrm;
    short[] defCol;

    public Mesh(VertexBuffer vertices, IndexBuffer[] submeshes, Appearance[] appearances) {
        if (vertices == null || submeshes == null) throw new NullPointerException();
        if (submeshes.length == 0 || (appearances != null && appearances.length < submeshes.length)) throw new IllegalArgumentException();
        for (int i = 0; i < submeshes.length; i++)
            if (submeshes[i] == null) throw new NullPointerException();
        vb = vertices;
        this.submeshes = new IndexBuffer[submeshes.length];
        System.arraycopy(submeshes, 0, this.submeshes, 0, submeshes.length);
        this.appearances = new Appearance[submeshes.length];
        if (appearances != null) System.arraycopy(appearances, 0, this.appearances, 0, submeshes.length);
    }

    public Mesh(VertexBuffer vertices, IndexBuffer submesh, Appearance appearance) {
        this(vertices, new IndexBuffer[] {submesh}, new Appearance[] {appearance});
    }

    Mesh() {}

    Object3D dup() {
        Mesh m = new Mesh();
        copyMeshTo(m);
        return m;
    }

    void copyMeshTo(Mesh m) {
        copyNodeTo(m);
        m.vb = vb;
        m.submeshes = new IndexBuffer[submeshes.length];
        System.arraycopy(submeshes, 0, m.submeshes, 0, submeshes.length);
        m.appearances = new Appearance[appearances.length];
        System.arraycopy(appearances, 0, m.appearances, 0, appearances.length);
    }

    int refs(Object3D[] out, int pos) {
        pos = super.refs(out, pos);
        pos = add(out, pos, vb);
        for (int i = 0; i < submeshes.length; i++) pos = add(out, pos, submeshes[i]);
        for (int i = 0; i < appearances.length; i++) pos = add(out, pos, appearances[i]);
        return pos;
    }

    public void setAppearance(int index, Appearance appearance) {
        if (index < 0 || index >= appearances.length) throw new IndexOutOfBoundsException();
        appearances[index] = appearance;
    }

    public Appearance getAppearance(int index) {
        if (index < 0 || index >= appearances.length) throw new IndexOutOfBoundsException();
        return appearances[index];
    }

    public IndexBuffer getIndexBuffer(int index) {
        if (index < 0 || index >= submeshes.length) throw new IndexOutOfBoundsException();
        return submeshes[index];
    }

    public VertexBuffer getVertexBuffer() { return vb; }

    public int getSubmeshCount() { return submeshes.length; }

    /** Updates defPos/defNrm/defCol before rendering. */
    void deform() {}
}
