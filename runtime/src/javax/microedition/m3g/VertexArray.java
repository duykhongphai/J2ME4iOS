package javax.microedition.m3g;

public class VertexArray extends Object3D {
    final int count, components, componentSize;
    final short[] data; /* byte arrays are stored sign-extended */

    public VertexArray(int numVertices, int numComponents, int componentSize) {
        if (numVertices < 1 || numVertices > 65535 || numComponents < 2 || numComponents > 4 || componentSize < 1 || componentSize > 2)
            throw new IllegalArgumentException();
        count = numVertices;
        components = numComponents;
        this.componentSize = componentSize;
        data = new short[numVertices * numComponents];
    }

    Object3D dup() {
        VertexArray v = new VertexArray(count, components, componentSize);
        System.arraycopy(data, 0, v.data, 0, data.length);
        return v;
    }

    public int getVertexCount() { return count; }

    public int getComponentCount() { return components; }

    public int getComponentType() { return componentSize; }

    private void check(int first, int num, int len) {
        if (num < 0 || first < 0 || first + num > count) throw new IndexOutOfBoundsException();
        if (len < num * components) throw new IllegalArgumentException();
    }

    public void set(int firstVertex, int numVertices, short[] values) {
        if (values == null) throw new NullPointerException();
        if (componentSize != 2) throw new IllegalStateException();
        check(firstVertex, numVertices, values.length);
        System.arraycopy(values, 0, data, firstVertex * components, numVertices * components);
    }

    public void set(int firstVertex, int numVertices, byte[] values) {
        if (values == null) throw new NullPointerException();
        if (componentSize != 1) throw new IllegalStateException();
        check(firstVertex, numVertices, values.length);
        int o = firstVertex * components;
        for (int i = 0; i < numVertices * components; i++) data[o + i] = values[i];
    }

    public void get(int firstVertex, int numVertices, short[] values) {
        if (values == null) throw new NullPointerException();
        if (componentSize != 2) throw new IllegalStateException();
        check(firstVertex, numVertices, values.length);
        System.arraycopy(data, firstVertex * components, values, 0, numVertices * components);
    }

    public void get(int firstVertex, int numVertices, byte[] values) {
        if (values == null) throw new NullPointerException();
        if (componentSize != 1) throw new IllegalStateException();
        check(firstVertex, numVertices, values.length);
        int o = firstVertex * components;
        for (int i = 0; i < numVertices * components; i++) values[i] = (byte) data[o + i];
    }
}
