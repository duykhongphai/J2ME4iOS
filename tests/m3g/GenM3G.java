import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Writes a small JSR-184 file (host JVM): a World with an animated, textured, lit cube,
 * a camera, a directional light and a background. Written straight from the file format
 * spec so the loader is checked against an independent encoder.
 */
public class GenM3G {
    static ByteArrayOutputStream objs = new ByteArrayOutputStream();
    static int nextIndex = 1;

    static class W {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        void u8(int v) { b.write(v & 255); }
        void bool(boolean v) { u8(v ? 1 : 0); }
        void u16(int v) { u8(v); u8(v >> 8); }
        void i32(int v) { u8(v); u8(v >> 8); u8(v >> 16); u8(v >> 24); }
        void f32(float f) { i32(Float.floatToIntBits(f)); }
        void str(String s) throws IOException { b.write(s.getBytes("UTF-8")); u8(0); }
        void rgb(int c) { u8(c >> 16); u8(c >> 8); u8(c); }
        void rgba(int c) { u8(c >> 16); u8(c >> 8); u8(c); u8(c >>> 24); }
        void object3D(int userID, int... tracks) { i32(userID); i32(tracks.length); for (int t : tracks) i32(t); i32(0); }
        void transformable(float[] t, float[] s, float angle, float[] axis) {
            bool(true);
            f32(t[0]); f32(t[1]); f32(t[2]);
            f32(s[0]); f32(s[1]); f32(s[2]);
            f32(angle); f32(axis[0]); f32(axis[1]); f32(axis[2]);
            bool(false);
        }
        void identityTransformable() { bool(false); bool(false); }
        void nodeTail() { bool(true); bool(true); u8(255); i32(-1); bool(false); }
    }

    static int emit(int type, W w) {
        byte[] d = w.b.toByteArray();
        objs.write(type);
        int n = d.length;
        objs.write(n); objs.write(n >> 8); objs.write(n >> 16); objs.write(n >> 24);
        objs.write(d, 0, d.length);
        return nextIndex++;
    }

    public static void main(String[] args) throws Exception {
        W h = new W();
        h.u8(1); h.u8(0); h.bool(false); h.i32(0); h.i32(0); h.str("J2ME4iOS test");
        emit(0, h); // header = index 1

        // animation: orientation around Y, 0..360 deg in 2000 ms (SLERP, 5 keys)
        W ks = new W();
        ks.object3D(0);
        ks.u8(177); ks.u8(193); ks.u8(0); // SLERP, LOOP, raw floats
        ks.i32(2000); ks.i32(0); ks.i32(4); ks.i32(4); ks.i32(5);
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(90 * k) / 2;
            ks.i32(500 * k);
            ks.f32(0); ks.f32((float) Math.sin(a)); ks.f32(0); ks.f32((float) Math.cos(a));
        }
        int ksi = emit(22, ks);
        W ac = new W();
        ac.object3D(0);
        ac.f32(1); ac.f32(1); ac.i32(0); ac.i32(0); ac.f32(0); ac.i32(0);
        int aci = emit(1, ac);
        W at = new W();
        at.object3D(0);
        at.i32(ksi); at.i32(aci); at.i32(268); // ORIENTATION
        int ati = emit(2, at);

        // cube: 6 faces x 4 vertices, byte positions (+-1), normals, texcoords
        int[][] faces = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        byte[] pos = new byte[72], nrm = new byte[72], tc = new byte[48];
        int v = 0;
        for (int[] n : faces) {
            int[] u = n[0] != 0 ? new int[] {0, 1, 0} : new int[] {1, 0, 0};
            int[] w = {n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0]};
            int[][] corners = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
            for (int[] c : corners) {
                for (int i = 0; i < 3; i++) {
                    pos[v * 3 + i] = (byte) (n[i] + c[0] * u[i] + c[1] * w[i]);
                    nrm[v * 3 + i] = (byte) (n[i] * 127);
                }
                tc[v * 2] = (byte) (c[0] > 0 ? 1 : 0);
                tc[v * 2 + 1] = (byte) (c[1] > 0 ? 1 : 0);
                v++;
            }
        }
        int posi = vertexArray(1, 3, pos, 24, true); // delta encoded to test the decoder
        int nrmi = vertexArray(1, 3, nrm, 24, false);
        int tci = vertexArray(1, 2, tc, 24, false);
        W vb = new W();
        vb.object3D(0);
        vb.rgba(0xFFFFFFFF);
        vb.i32(posi); vb.f32(0); vb.f32(0); vb.f32(0); vb.f32(0.5f);
        vb.i32(nrmi); vb.i32(0);
        vb.i32(1); vb.i32(tci); vb.f32(0); vb.f32(0); vb.f32(0); vb.f32(1);
        int vbi = emit(20, vb);
        // strips: winding of each face strip must be CCW from outside
        W tsa = new W();
        tsa.object3D(0);
        tsa.u8(129); // explicit byte indices
        tsa.i32(24);
        for (int f = 0; f < 6; f++) {
            // corners (-,-),(+,-),(-,+),(+,+) form CCW strip when u x w == n
            tsa.u8(f * 4); tsa.u8(f * 4 + 1); tsa.u8(f * 4 + 2); tsa.u8(f * 4 + 3);
        }
        tsa.i32(6);
        for (int f = 0; f < 6; f++) tsa.i32(4);
        int tsai = emit(18, tsa);

        W mat = new W();
        mat.object3D(0);
        mat.rgb(0x404040); mat.rgba(0xFFFFFFFF); mat.rgb(0x000000); mat.rgb(0xFFFFFF); mat.f32(32); mat.bool(false);
        int mati = emit(11, mat);
        // 8x8 RGB checker, palette-based (2 entries) to test palettes
        W img = new W();
        img.object3D(0);
        img.u8(99); img.bool(false); img.i32(8); img.i32(8);
        img.i32(6); img.rgb(0xE04020); img.rgb(0xF0E0A0);
        img.i32(64);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) img.u8(((x >> 1) + (y >> 1)) & 1);
        int imgi = emit(8, img);
        W tex = new W();
        tex.object3D(0);
        tex.identityTransformable();
        tex.i32(imgi); tex.rgb(0); tex.u8(227); tex.u8(241); tex.u8(241); tex.u8(208); tex.u8(210);
        int texi = emit(17, tex);
        W pm = new W();
        pm.object3D(0);
        pm.u8(160); pm.u8(165); pm.u8(168); pm.bool(false); pm.bool(false); pm.bool(true);
        int pmi = emit(14, pm);
        W app = new W();
        app.object3D(0);
        app.u8(0); app.i32(0); app.i32(0); app.i32(pmi); app.i32(mati); app.i32(1); app.i32(texi);
        int appi = emit(3, app);
        W mesh = new W();
        mesh.object3D(42, ati);
        mesh.transformable(new float[] {0, 0, 0}, new float[] {1, 1, 1}, 0, new float[] {0, 1, 0});
        mesh.nodeTail();
        mesh.i32(vbi); mesh.i32(1); mesh.i32(tsai); mesh.i32(appi);
        int meshi = emit(12, mesh);

        W cam = new W();
        cam.object3D(7);
        cam.transformable(new float[] {0, 1.6f, 4.5f}, new float[] {1, 1, 1}, -20, new float[] {1, 0, 0});
        cam.nodeTail();
        cam.u8(50); cam.f32(50); cam.f32(240f / 320f); cam.f32(0.5f); cam.f32(50);
        int cami = emit(5, cam);
        W light = new W();
        light.object3D(0);
        light.transformable(new float[] {0, 0, 0}, new float[] {1, 1, 1}, -45, new float[] {1, 1, 0});
        light.nodeTail();
        light.f32(1); light.f32(0); light.f32(0); light.rgb(0xFFFFFF); light.u8(129); light.f32(1); light.f32(45); light.f32(0);
        int lighti = emit(10, light);
        W amb = new W();
        amb.object3D(0);
        amb.identityTransformable();
        amb.nodeTail();
        amb.f32(1); amb.f32(0); amb.f32(0); amb.rgb(0x606060); amb.u8(128); amb.f32(1); amb.f32(45); amb.f32(0);
        int ambi = emit(10, amb);
        W bg = new W();
        bg.object3D(0);
        bg.rgba(0xFF203050); bg.i32(0); bg.u8(32); bg.u8(32); bg.i32(0); bg.i32(0); bg.i32(0); bg.i32(0); bg.bool(true); bg.bool(true);
        int bgi = emit(4, bg);
        W world = new W();
        world.object3D(1);
        world.identityTransformable();
        world.nodeTail();
        world.i32(4); world.i32(meshi); world.i32(cami); world.i32(lighti); world.i32(ambi);
        world.i32(cami); world.i32(bgi);
        emit(21, world);

        byte[] data = objs.toByteArray();
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write(new byte[] {(byte) 0xAB, 0x4A, 0x53, 0x52, 0x31, 0x38, 0x34, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A});
        // two sections: header uncompressed would be typical, but one zlib-compressed section tests inflate
        java.util.zip.Deflater def = new java.util.zip.Deflater();
        def.setInput(data);
        def.finish();
        byte[] comp = new byte[data.length + 64];
        int cl = def.deflate(comp);
        DataOutputStream dos = new DataOutputStream(file);
        file.write(1);
        writeLE(file, 13 + cl);
        writeLE(file, data.length);
        file.write(comp, 0, cl);
        java.util.zip.Adler32 ad = new java.util.zip.Adler32();
        ad.update(comp, 0, cl);
        writeLE(file, (int) ad.getValue());
        try (FileOutputStream fo = new FileOutputStream(args[0])) {
            fo.write(file.toByteArray());
        }
        System.out.println("wrote " + args[0] + " (" + file.size() + " bytes, " + (nextIndex - 1) + " objects)");
    }

    static void writeLE(ByteArrayOutputStream o, int v) { o.write(v); o.write(v >> 8); o.write(v >> 16); o.write(v >> 24); }

    static int vertexArray(int size, int comps, byte[] data, int count, boolean delta) {
        W w = new W();
        w.object3D(0);
        w.u8(size); w.u8(comps); w.u8(delta ? 1 : 0); w.u16(count);
        for (int i = 0; i < count * comps; i++) {
            int val = data[i];
            if (delta && i >= comps) val = (byte) (data[i] - data[i - comps]);
            w.u8(val);
        }
        return emit(19, w);
    }
}
