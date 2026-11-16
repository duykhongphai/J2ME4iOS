package javax.microedition.m3g;

public class VertexBuffer extends Object3D {
    static final int MAX_UNITS = 2;

    VertexArray positions, normals, colors;
    float posScale = 1;
    float[] posBias = new float[3];
    final VertexArray[] texCoords = new VertexArray[MAX_UNITS];
    final float[] texScale = {1, 1};
    final float[][] texBias = new float[MAX_UNITS][3];
    int defaultColor = 0xFFFFFFFF;

    public VertexBuffer() {}

    Object3D dup() {
        VertexBuffer v = new VertexBuffer();
        v.positions = positions;
        v.normals = normals;
        v.colors = colors;
        v.posScale = posScale;
        System.arraycopy(posBias, 0, v.posBias, 0, 3);
        for (int i = 0; i < MAX_UNITS; i++) {
            v.texCoords[i] = texCoords[i];
            v.texScale[i] = texScale[i];
            System.arraycopy(texBias[i], 0, v.texBias[i], 0, 3);
        }
        v.defaultColor = defaultColor;
        return v;
    }

    int refs(Object3D[] out, int pos) {
        pos = super.refs(out, pos);
        pos = add(out, pos, positions);
        pos = add(out, pos, normals);
        pos = add(out, pos, colors);
        for (int i = 0; i < MAX_UNITS; i++) pos = add(out, pos, texCoords[i]);
        return pos;
    }

    public int getVertexCount() {
        if (positions != null) return positions.count;
        if (normals != null) return normals.count;
        if (colors != null) return colors.count;
        for (int i = 0; i < MAX_UNITS; i++)
            if (texCoords[i] != null) return texCoords[i].count;
        return 0;
    }

    private void checkCount(VertexArray a, VertexArray self) {
        if (a == null) return;
        int n = 0;
        if (positions != null && positions != self) n = positions.count;
        else if (normals != null && normals != self) n = normals.count;
        else if (colors != null && colors != self) n = colors.count;
        if (n != 0 && n != a.count) throw new IllegalArgumentException("Vertex count mismatch");
    }

    public void setPositions(VertexArray positions, float scale, float[] bias) {
        if (positions != null) {
            if (positions.components != 3) throw new IllegalArgumentException("Positions need 3 components");
            checkCount(positions, this.positions);
        }
        this.positions = positions;
        posScale = scale;
        if (bias != null) {
            if (bias.length < 3) throw new IllegalArgumentException();
            System.arraycopy(bias, 0, posBias, 0, 3);
        } else posBias[0] = posBias[1] = posBias[2] = 0;
    }

    public VertexArray getPositions(float[] scaleBias) {
        if (scaleBias != null && positions != null) {
            if (scaleBias.length < 4) throw new IllegalArgumentException();
            scaleBias[0] = posScale;
            System.arraycopy(posBias, 0, scaleBias, 1, 3);
        }
        return positions;
    }

    public void setTexCoords(int index, VertexArray texCoords, float scale, float[] bias) {
        if (index < 0 || index >= MAX_UNITS) throw new IndexOutOfBoundsException();
        if (texCoords != null) {
            if (texCoords.components == 4) throw new IllegalArgumentException();
            checkCount(texCoords, this.texCoords[index]);
        }
        this.texCoords[index] = texCoords;
        texScale[index] = scale;
        float[] b = texBias[index];
        if (bias != null) {
            if (bias.length < texCoords.components) throw new IllegalArgumentException();
            b[0] = bias[0];
            b[1] = bias[1];
            b[2] = bias.length > 2 ? bias[2] : 0;
        } else b[0] = b[1] = b[2] = 0;
    }

    public VertexArray getTexCoords(int index, float[] scaleBias) {
        if (index < 0 || index >= MAX_UNITS) throw new IndexOutOfBoundsException();
        VertexArray a = texCoords[index];
        if (scaleBias != null && a != null) {
            if (scaleBias.length < a.components + 1) throw new IllegalArgumentException();
            scaleBias[0] = texScale[index];
            for (int i = 0; i < a.components; i++) scaleBias[i + 1] = texBias[index][i];
        }
        return a;
    }

    public void setNormals(VertexArray normals) {
        if (normals != null) {
            if (normals.components != 3) throw new IllegalArgumentException("Normals need 3 components");
            checkCount(normals, this.normals);
        }
        this.normals = normals;
    }

    public VertexArray getNormals() { return normals; }

    public void setColors(VertexArray colors) {
        if (colors != null) {
            if (colors.componentSize != 1 || colors.components < 3) throw new IllegalArgumentException("Colors must be 3 or 4 bytes");
            checkCount(colors, this.colors);
        }
        this.colors = colors;
    }

    public VertexArray getColors() { return colors; }

    public void setDefaultColor(int argb) { defaultColor = argb; }

    public int getDefaultColor() { return defaultColor; }

    boolean acceptsProperty(int p) { return p == AnimationTrack.ALPHA || p == AnimationTrack.COLOR || super.acceptsProperty(p); }

    void applyAnimation(int property, float[] v, int comps) {
        if (property == AnimationTrack.COLOR) defaultColor = colorFrom(v, 3, defaultColor);
        else if (property == AnimationTrack.ALPHA) defaultColor = (defaultColor & 0xFFFFFF) | (clamp255(v[0]) << 24);
        else super.applyAnimation(property, v, comps);
    }
}
