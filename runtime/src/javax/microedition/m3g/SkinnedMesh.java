package javax.microedition.m3g;

import java.util.Vector;

public class SkinnedMesh extends Mesh {
    Group skeleton;
    final Vector bones = new Vector(); /* Bone */

    static final class Bone {
        Node node;
        int weight, first, count;
        float[] atRestInv = new float[16];
        float[] atRest = new float[16];
    }

    public SkinnedMesh(VertexBuffer vertices, IndexBuffer[] submeshes, Appearance[] appearances, Group skeleton) {
        super(vertices, submeshes, appearances);
        setSkeleton(skeleton);
    }

    public SkinnedMesh(VertexBuffer vertices, IndexBuffer submesh, Appearance appearance, Group skeleton) {
        super(vertices, submesh, appearance);
        setSkeleton(skeleton);
    }

    private SkinnedMesh() {}

    private void setSkeleton(Group skeleton) {
        if (skeleton == null) throw new NullPointerException();
        if (skeleton instanceof World || skeleton.parent != null) throw new IllegalArgumentException();
        this.skeleton = skeleton;
        skeleton.parent = this;
    }

    Object3D dup() {
        SkinnedMesh m = new SkinnedMesh();
        copyMeshTo(m);
        Group sk = (Group) skeleton.duplicate();
        m.skeleton = sk;
        sk.parent = m;
        for (int i = 0; i < bones.size(); i++) {
            Bone b = (Bone) bones.elementAt(i);
            Bone nb = new Bone();
            nb.node = mapNode(skeleton, sk, b.node);
            nb.weight = b.weight;
            nb.first = b.first;
            nb.count = b.count;
            System.arraycopy(b.atRestInv, 0, nb.atRestInv, 0, 16);
            System.arraycopy(b.atRest, 0, nb.atRest, 0, 16);
            m.bones.addElement(nb);
        }
        return m;
    }

    /** Finds the node in the duplicated skeleton at the same position as n in the original. */
    private static Node mapNode(Node orig, Node copy, Node n) {
        if (orig == n) return copy;
        if (orig instanceof Group && copy instanceof Group) {
            Group go = (Group) orig, gc = (Group) copy;
            for (int i = 0; i < go.children.size() && i < gc.children.size(); i++) {
                Node r = mapNode((Node) go.children.elementAt(i), (Node) gc.children.elementAt(i), n);
                if (r != null) return r;
            }
        }
        return null;
    }

    int refs(Object3D[] out, int pos) { return add(out, super.refs(out, pos), skeleton); }

    void alignTree(Node reference) {
        super.alignTree(reference);
        skeleton.alignTree(reference);
    }

    public Group getSkeleton() { return skeleton; }

    private boolean inSkeleton(Node n) {
        for (Node p = n; p != null; p = p.parent)
            if (p == skeleton) return true;
        return false;
    }

    /** bone -> mesh transform */
    private void boneToMesh(Node bone, float[] out) {
        new Transform().get(out);
        float[] tmp = new float[16];
        for (Node p = bone; p != null && p != this; p = p.parent) {
            p.composite(tmp);
            Transform.mul(tmp, out, out);
        }
    }

    public void addTransform(Node bone, int weight, int firstVertex, int numVertices) {
        if (bone == null) throw new NullPointerException();
        if (!inSkeleton(bone)) throw new IllegalArgumentException("Bone not in skeleton");
        if (weight <= 0 || numVertices <= 0) throw new IllegalArgumentException();
        if (firstVertex < 0 || firstVertex + numVertices > 65535) throw new IndexOutOfBoundsException();
        Bone b = new Bone();
        b.node = bone;
        b.weight = weight;
        b.first = firstVertex;
        b.count = numVertices;
        boneToMesh(bone, b.atRest);
        if (!Transform.invert(b.atRest, b.atRestInv)) new Transform().get(b.atRestInv);
        bones.addElement(b);
    }

    public void getBoneTransform(Node bone, Transform transform) {
        if (bone == null || transform == null) throw new NullPointerException();
        if (!inSkeleton(bone)) throw new IllegalArgumentException();
        for (int i = 0; i < bones.size(); i++) {
            Bone b = (Bone) bones.elementAt(i);
            if (b.node == bone) {
                transform.set(b.atRest);
                return;
            }
        }
        transform.setIdentity();
    }

    public int getBoneVertices(Node bone, int[] indices, float[] weights) {
        if (bone == null) throw new NullPointerException();
        if (!inSkeleton(bone)) throw new IllegalArgumentException();
        int n = vb.getVertexCount();
        float[] total = new float[n], mine = new float[n];
        for (int i = 0; i < bones.size(); i++) {
            Bone b = (Bone) bones.elementAt(i);
            for (int v = b.first; v < b.first + b.count && v < n; v++) {
                total[v] += b.weight;
                if (b.node == bone) mine[v] += b.weight;
            }
        }
        int c = 0;
        for (int v = 0; v < n; v++) {
            if (mine[v] <= 0) continue;
            if (indices != null && weights != null) {
                if (c >= indices.length || c >= weights.length) throw new IllegalArgumentException();
                indices[c] = v;
                weights[c] = mine[v] / total[v];
            }
            c++;
        }
        return c;
    }

    void deform() {
        VertexArray pa = vb.positions;
        if (pa == null || bones.isEmpty()) {
            defPos = null;
            defNrm = null;
            return;
        }
        int n = pa.count;
        if (defPos == null || defPos.length != n * 3) defPos = new float[n * 3];
        VertexArray na = vb.normals;
        if (na != null && (defNrm == null || defNrm.length != n * 3)) defNrm = new float[n * 3];
        if (na == null) defNrm = null;
        float[] acc = new float[n * 3], nacc = na != null ? new float[n * 3] : null;
        float[] wsum = new float[n];
        float[] cur = new float[16], m = new float[16];
        float s = vb.posScale;
        float[] bias = vb.posBias;
        for (int i = 0; i < bones.size(); i++) {
            Bone b = (Bone) bones.elementAt(i);
            boneToMesh(b.node, cur);
            Transform.mul(cur, b.atRestInv, m);
            int end = Math.min(n, b.first + b.count);
            for (int v = b.first; v < end; v++) {
                float x = pa.data[v * 3] * s + bias[0], y = pa.data[v * 3 + 1] * s + bias[1], z = pa.data[v * 3 + 2] * s + bias[2];
                float w = b.weight;
                acc[v * 3] += w * (m[0] * x + m[1] * y + m[2] * z + m[3]);
                acc[v * 3 + 1] += w * (m[4] * x + m[5] * y + m[6] * z + m[7]);
                acc[v * 3 + 2] += w * (m[8] * x + m[9] * y + m[10] * z + m[11]);
                if (nacc != null) {
                    float nx = na.data[v * 3], ny = na.data[v * 3 + 1], nz = na.data[v * 3 + 2];
                    nacc[v * 3] += w * (m[0] * nx + m[1] * ny + m[2] * nz);
                    nacc[v * 3 + 1] += w * (m[4] * nx + m[5] * ny + m[6] * nz);
                    nacc[v * 3 + 2] += w * (m[8] * nx + m[9] * ny + m[10] * nz);
                }
                wsum[v] += w;
            }
        }
        for (int v = 0; v < n; v++) {
            if (wsum[v] > 0) {
                float iw = 1 / wsum[v];
                defPos[v * 3] = acc[v * 3] * iw;
                defPos[v * 3 + 1] = acc[v * 3 + 1] * iw;
                defPos[v * 3 + 2] = acc[v * 3 + 2] * iw;
                if (defNrm != null) {
                    defNrm[v * 3] = nacc[v * 3];
                    defNrm[v * 3 + 1] = nacc[v * 3 + 1];
                    defNrm[v * 3 + 2] = nacc[v * 3 + 2];
                }
            } else {
                defPos[v * 3] = pa.data[v * 3] * s + bias[0];
                defPos[v * 3 + 1] = pa.data[v * 3 + 1] * s + bias[1];
                defPos[v * 3 + 2] = pa.data[v * 3 + 2] * s + bias[2];
                if (defNrm != null) {
                    defNrm[v * 3] = na.data[v * 3];
                    defNrm[v * 3 + 1] = na.data[v * 3 + 1];
                    defNrm[v * 3 + 2] = na.data[v * 3 + 2];
                }
            }
        }
    }
}
