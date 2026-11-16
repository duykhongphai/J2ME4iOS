package javax.microedition.m3g;

public class MorphingMesh extends Mesh {
    VertexBuffer[] targets;
    float[] weights;

    public MorphingMesh(VertexBuffer base, VertexBuffer[] targets, IndexBuffer[] submeshes, Appearance[] appearances) {
        super(base, submeshes, appearances);
        setTargets(targets);
    }

    public MorphingMesh(VertexBuffer base, VertexBuffer[] targets, IndexBuffer submesh, Appearance appearance) {
        super(base, submesh, appearance);
        setTargets(targets);
    }

    private MorphingMesh() {}

    private void setTargets(VertexBuffer[] t) {
        if (t == null) throw new NullPointerException();
        for (int i = 0; i < t.length; i++)
            if (t[i] == null) throw new NullPointerException();
        targets = new VertexBuffer[t.length];
        System.arraycopy(t, 0, targets, 0, t.length);
        weights = new float[t.length];
    }

    Object3D dup() {
        MorphingMesh m = new MorphingMesh();
        copyMeshTo(m);
        m.targets = new VertexBuffer[targets.length];
        System.arraycopy(targets, 0, m.targets, 0, targets.length);
        m.weights = new float[weights.length];
        System.arraycopy(weights, 0, m.weights, 0, weights.length);
        return m;
    }

    int refs(Object3D[] out, int pos) {
        pos = super.refs(out, pos);
        for (int i = 0; i < targets.length; i++) pos = add(out, pos, targets[i]);
        return pos;
    }

    public VertexBuffer getMorphTarget(int index) {
        if (index < 0 || index >= targets.length) throw new IndexOutOfBoundsException();
        return targets[index];
    }

    public int getMorphTargetCount() { return targets.length; }

    public void setWeights(float[] weights) {
        if (weights == null) throw new NullPointerException();
        if (weights.length < targets.length) throw new IllegalArgumentException();
        System.arraycopy(weights, 0, this.weights, 0, targets.length);
    }

    public void getWeights(float[] weights) {
        if (weights == null) throw new NullPointerException();
        if (weights.length < targets.length) throw new IllegalArgumentException();
        System.arraycopy(this.weights, 0, weights, 0, targets.length);
    }

    boolean acceptsProperty(int p) { return p == AnimationTrack.MORPH_WEIGHTS || super.acceptsProperty(p); }

    void applyAnimation(int property, float[] v, int comps) {
        if (property == AnimationTrack.MORPH_WEIGHTS) {
            for (int i = 0; i < weights.length; i++) weights[i] = i < comps ? v[i] : 0;
        } else super.applyAnimation(property, v, comps);
    }

    void deform() {
        boolean any = false;
        for (int i = 0; i < weights.length; i++)
            if (weights[i] != 0) any = true;
        if (!any) {
            defPos = null;
            defNrm = null;
            defCol = null;
            return;
        }
        VertexArray pa = vb.positions;
        int n = vb.getVertexCount();
        if (pa != null) {
            if (defPos == null || defPos.length != n * 3) defPos = new float[n * 3];
            float s = vb.posScale;
            float[] b = vb.posBias;
            for (int v = 0; v < n; v++)
                for (int c = 0; c < 3; c++) defPos[v * 3 + c] = pa.data[v * 3 + c] * s + b[c];
            for (int t = 0; t < targets.length; t++) {
                VertexArray ta = targets[t].positions;
                float w = weights[t];
                if (ta == null || w == 0 || ta.count < n) continue;
                float ts = targets[t].posScale;
                float[] tb = targets[t].posBias;
                for (int v = 0; v < n; v++)
                    for (int c = 0; c < 3; c++) {
                        float base = pa.data[v * 3 + c] * s + b[c];
                        defPos[v * 3 + c] += w * (ta.data[v * 3 + c] * ts + tb[c] - base);
                    }
            }
        } else defPos = null;
        VertexArray na = vb.normals;
        if (na != null) {
            if (defNrm == null || defNrm.length != n * 3) defNrm = new float[n * 3];
            for (int i = 0; i < n * 3; i++) defNrm[i] = na.data[i];
            for (int t = 0; t < targets.length; t++) {
                VertexArray ta = targets[t].normals;
                float w = weights[t];
                if (ta == null || w == 0 || ta.count < n) continue;
                float k = (na.componentSize == ta.componentSize) ? 1 : (ta.componentSize == 1 ? 32767f / 127f : 127f / 32767f);
                for (int i = 0; i < n * 3; i++) defNrm[i] += w * (ta.data[i] * k - na.data[i]);
            }
        } else defNrm = null;
        VertexArray ca = vb.colors;
        if (ca != null) {
            int cc = ca.components;
            if (defCol == null || defCol.length != n * cc) defCol = new short[n * cc];
            for (int i = 0; i < n * cc; i++) {
                float base = ca.data[i] & 255, val = base;
                for (int t = 0; t < targets.length; t++) {
                    VertexArray ta = targets[t].colors;
                    if (ta == null || ta.components != cc || ta.count < n || weights[t] == 0) continue;
                    val += weights[t] * ((ta.data[i] & 255) - base);
                }
                int iv = (int) (val + 0.5f);
                defCol[i] = (short) (iv < 0 ? 0 : (iv > 255 ? 255 : iv));
            }
        } else defCol = null;
    }
}
