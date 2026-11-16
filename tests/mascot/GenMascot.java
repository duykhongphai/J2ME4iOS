import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Writes cube.mbac (v4, bit-packed vertices/normals/polygons, 2 bones), cube.mtra and tex.bmp for MascotTest. */
public class GenMascot {
    static class Out {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int buf, cnt;
        void u8(int v) { flushBits(); b.write(v & 255); }
        void u16(int v) { u8(v); u8(v >> 8); }
        void s32(int v) { u16(v); u16(v >> 16); }
        void bits(int v, int n) {
            buf |= (v & ((1 << n) - 1)) << cnt;
            cnt += n;
            while (cnt >= 8) { b.write(buf & 255); buf >>>= 8; cnt -= 8; }
        }
        void flushBits() { if (cnt > 0) { b.write(buf & 255); buf = 0; cnt = 0; } }
    }

    public static void main(String[] a) throws IOException {
        String dir = a[0];
        // cube of size 2*S centred at origin; bone 0 = root (owns 12 verts), bone 1 = child (owns 12 verts) shifted down
        int S = 300;
        int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        int[] pos = new int[72];
        int[] nrm = new int[72];
        int v = 0;
        for (int[] n : faces) {
            int[] u = n[0] != 0 ? new int[] {0, 1, 0} : new int[] {1, 0, 0};
            int[] w = {n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0]};
            int[][] c = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (int[] cc : c) {
                for (int i = 0; i < 3; i++) {
                    pos[v * 3 + i] = (n[i] + cc[0] * u[i] + cc[1] * w[i]) * S;
                    nrm[v * 3 + i] = n[i];
                }
                v++;
            }
        }
        Out o = new Out();
        o.u8('M'); o.u8('B'); o.u8(4); o.u8(0);
        o.u8(2); o.u8(2); o.u8(2); o.u8(1); // vertex fmt 2, normal fmt 2, polygon fmt 2, bone fmt 1
        o.u16(24); o.u16(0); o.u16(6); o.u16(2); // vertices, T3, T4, bones
        // vertices: one block of 24 with 13-bit coordinates (type 2)
        o.bits((2 << 6) | 23, 8);
        for (int i = 0; i < 72; i++) o.bits(pos[i], 13);
        o.flushBits();
        // normals: axis codes (x == 64 then 3-bit t): t 0:+x 1:+y 2:+z 3:-x 4:-y 5:-z
        for (int i = 0; i < 24; i++) {
            int t = nrm[i * 3] == 1 ? 0 : nrm[i * 3] == -1 ? 3 : nrm[i * 3 + 1] == 1 ? 1 : nrm[i * 3 + 1] == -1 ? 4 : nrm[i * 3 + 2] == 1 ? 2 : 5;
            o.bits(64, 7);
            o.bits(t, 3);
        }
        o.flushBits();
        // textured quads (format 2): u8 matBits, u8 idxBits, then bit-packed polys with 7-bit UVs
        o.u8(7); o.u8(5);
        for (int f = 0; f < 6; f++) {
            int mat = 32 | (f == 4 ? 1 : 0); // lighting; the +Z face uses the color key
            o.bits(mat, 7);
            // quad (a,b,c,d) splits into (a,b,c),(c,b,d): order corners 0,1,3,2
            int base = f * 4;
            o.bits(base, 5); o.bits(base + 3, 5); o.bits(base + 1, 5); o.bits(base + 2, 5); // clockwise from outside (Mascot convention)
            int[] uv = {0, 0, 0, 63, 63, 0, 63, 63};
            for (int k = 0; k < 8; k++) o.bits(uv[k], 7);
        }
        o.flushBits();
        // bones: root identity, child translated by (0, 0, 0) and owning the second half
        int[] id = {4096, 0, 0, 0, 0, 4096, 0, 0, 0, 0, 4096, 0};
        o.u16(12); o.u16(-1 & 0xFFFF); for (int k : id) o.u16(k);
        o.u16(12); o.u16(0); for (int k : id) o.u16(k);
        for (int i = 0; i < 20; i++) o.u8(0);
        write(dir + "/cube.mbac", o.b.toByteArray());

        // MTRA v4: one action of 60 frames; bone 0: type 4 rotate track (spin about Y) + roll track; bone 1: type 1
        Out t = new Out();
        t.u8('M'); t.u8('T'); t.u8(4); t.u8(0);
        t.u16(1); t.u16(2);
        for (int i = 0; i < 8; i++) t.u16(0);
        t.s32(0);
        t.u16(60);
        t.u8(4);
        t.u16(5); // rotate track: direction of Z axis around Y
        for (int k = 0; k < 5; k++) {
            double ang = Math.PI * 2 * k / 4;
            t.u16(k * 15); t.u16(0x10000 + (int) (Math.sin(ang) * 4096)); t.u16(0); t.u16((int) (Math.cos(ang) * 4096));
        }
        t.u16(1); t.u16(0); t.u16(0); // roll track: one key, 0
        t.u8(1);
        for (int i = 0; i < 20; i++) t.u8(0);
        write(dir + "/cube.mtra", t.b.toByteArray());

        // 64x64 8-bit BMP: checker of palette 1/2, index 0 in a border (transparent with color key)
        int W = 64, H = 64;
        ByteArrayOutputStream bmp = new ByteArrayOutputStream();
        int pal = 256 * 4, raster = 14 + 40 + pal;
        Out h = new Out();
        h.u8('B'); h.u8('M'); h.s32(raster + W * H); h.s32(0); h.s32(raster);
        h.s32(40); h.s32(W); h.s32(H); h.u16(1); h.u16(8); h.s32(0); h.s32(W * H); h.s32(2835); h.s32(2835); h.s32(256); h.s32(0);
        int[] colors = {0xFF00FF, 0x20A040, 0xF0F0F0, 0xE04010};
        for (int i = 0; i < 256; i++) { int c = i < 4 ? colors[i] : 0; h.u8(c); h.u8(c >> 8); h.u8(c >> 16); h.u8(0); }
        for (int y = H - 1; y >= 0; y--)
            for (int x = 0; x < W; x++) {
                int idx = (x < 6 || y < 6 || x >= W - 6 || y >= H - 6) ? 0 : (((x >> 3) + (y >> 3)) & 1) + 1;
                if (y < 20 && x > 40) idx = 3;
                h.u8(idx);
            }
        write(dir + "/tex.bmp", h.b.toByteArray());
        System.out.println("wrote mascot assets to " + dir);
    }

    static void write(String f, byte[] d) throws IOException {
        try (FileOutputStream fo = new FileOutputStream(f)) { fo.write(d); }
    }
}
