package javax.microedition.m3g;

public class Appearance extends Object3D {
    int layer;
    CompositingMode compositing;
    Fog fog;
    PolygonMode polygon;
    Material material;
    final Texture2D[] textures = new Texture2D[VertexBuffer.MAX_UNITS];

    public Appearance() {}

    Object3D dup() {
        Appearance a = new Appearance();
        a.layer = layer;
        a.compositing = compositing;
        a.fog = fog;
        a.polygon = polygon;
        a.material = material;
        System.arraycopy(textures, 0, a.textures, 0, textures.length);
        return a;
    }

    int refs(Object3D[] out, int pos) {
        pos = super.refs(out, pos);
        pos = add(out, pos, compositing);
        pos = add(out, pos, fog);
        pos = add(out, pos, polygon);
        pos = add(out, pos, material);
        for (int i = 0; i < textures.length; i++) pos = add(out, pos, textures[i]);
        return pos;
    }

    public void setLayer(int layer) {
        if (layer < -63 || layer > 63) throw new IndexOutOfBoundsException();
        this.layer = layer;
    }

    public int getLayer() { return layer; }

    public void setFog(Fog fog) { this.fog = fog; }

    public Fog getFog() { return fog; }

    public void setPolygonMode(PolygonMode polygonMode) { polygon = polygonMode; }

    public PolygonMode getPolygonMode() { return polygon; }

    public void setCompositingMode(CompositingMode compositingMode) { compositing = compositingMode; }

    public CompositingMode getCompositingMode() { return compositing; }

    public void setTexture(int index, Texture2D texture) {
        if (index < 0 || index >= textures.length) throw new IndexOutOfBoundsException();
        textures[index] = texture;
    }

    public Texture2D getTexture(int index) {
        if (index < 0 || index >= textures.length) throw new IndexOutOfBoundsException();
        return textures[index];
    }

    public void setMaterial(Material material) { this.material = material; }

    public Material getMaterial() { return material; }
}
