package com.mascotcapsule.micro3d.v3;

import java.io.IOException;

/** MBAC model: bone-skinned vertices, textured and colored polygons. */
public class Figure {
    static final int M_TRANSPARENT = 1, M_BLEND_MASK = 6, M_DOUBLE_FACE = 16, M_LIGHTING = 32, M_SPECULAR = 64;

    int numVertices, numPatterns = 1, numTextures = 1;
    float[] baseVerts, baseNormals; /* raw file values */
    float[] verts, normals;         /* posed, model space */
    int numBones;
    int[] boneLen, boneParent;
    float[] boneMat; /* 12 per bone */

    /* textured triangles (quads are split) */
    int numTex;
    int[] tIdx, tMat, tTex, tPat;
    int[] tUV; /* 6 per triangle, 0..255 */
    /* colored triangles */
    int numCol;
    int[] cIdx, cMat, cColor, cPat;

    int patternMask;
    Texture[] textures;
    int selected = -1;
    private boolean disposed;

    public Figure(byte[] b) {
        if (b == null) throw new NullPointerException();
        try {
            parse(b);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RuntimeException("Truncated MBAC data");
        }
        pose(null);
    }

    public Figure(String name) throws IOException { this(readResource(name)); }

    static byte[] readResource(String name) throws IOException {
        if (name == null) throw new NullPointerException();
        String p = name;
        while (p.startsWith("/")) p = p.substring(1);
        byte[] d = j4i.Sys.getResource(p);
        if (d == null) throw new IOException("Resource not found: " + name);
        return d;
    }

    public final void dispose() { disposed = true; }

    public final int getNumPattern() { return numPatterns; }

    public final int getNumTextures() { return textures == null ? 0 : textures.length; }

    public final Texture getTexture() {
        if (textures == null || selected < 0 || selected >= textures.length) return null;
        return textures[selected];
    }

    public final void selectTexture(int idx) {
        if (textures == null || idx < 0 || idx >= textures.length) throw new IllegalArgumentException();
        selected = idx;
    }

    public final void setTexture(Texture tex) {
        if (tex == null) throw new NullPointerException();
        if (!tex.forModel) throw new IllegalArgumentException("Texture is not for a model");
        textures = new Texture[] {tex};
        selected = 0;
    }

    public final void setTexture(Texture[] t) {
        if (t == null) throw new NullPointerException();
        if (t.length == 0) throw new IllegalArgumentException();
        for (int i = 0; i < t.length; i++) {
            if (t[i] == null) throw new NullPointerException();
            if (!t[i].forModel) throw new IllegalArgumentException("Texture is not for a model");
        }
        textures = new Texture[t.length];
        System.arraycopy(t, 0, textures, 0, t.length);
        selected = -1;
    }

    public final void setPattern(int idx) { patternMask = idx; }

    public final void setPosture(ActionTable t, int action, int frame) {
        if (t == null) throw new NullPointerException();
        if (action < 0 || action >= t.getNumActions()) throw new IllegalArgumentException();
        if (frame < 0) frame = 0;
        int dyn = t.dynamicPattern(action, frame);
        if (dyn != -1) patternMask = dyn;
        float[] act = new float[Math.max(numBones, t.numBones) * 12];
        t.pose(action, frame, act);
        pose(t.numBones < numBones ? trim(act, t.numBones) : act);
    }

    private static float[] trim(float[] a, int bones) {
        float[] r = new float[bones * 12];
        System.arraycopy(a, 0, r, 0, r.length);
        return r;
    }

    /** world_i = world_parent * bind_i * action_i; each bone owns the next boneLen[i] vertices. */
    void pose(float[] action) {
        if (verts == null) verts = new float[numVertices * 3];
        if (baseNormals != null && normals == null) normals = new float[numVertices * 3];
        if (numBones == 0) {
            System.arraycopy(baseVerts, 0, verts, 0, verts.length);
            if (normals != null) System.arraycopy(baseNormals, 0, normals, 0, normals.length);
            return;
        }
        float[] world = new float[numBones * 12];
        float[] tmp = new float[12];
        int v = 0;
        for (int i = 0; i < numBones; i++) {
            int o = i * 12;
            int par = boneParent[i];
            if (par >= 0 && par < i) mul(world, par * 12, boneMat, o, world, o);
            else System.arraycopy(boneMat, o, world, o, 12);
            if (action != null && (i + 1) * 12 <= action.length) {
                mul(world, o, action, o, tmp, 0);
                System.arraycopy(tmp, 0, world, o, 12);
            }
            int end = Math.min(numVertices, v + boneLen[i]);
            for (; v < end; v++) {
                float x = baseVerts[v * 3], y = baseVerts[v * 3 + 1], z = baseVerts[v * 3 + 2];
                verts[v * 3] = world[o] * x + world[o + 1] * y + world[o + 2] * z + world[o + 3];
                verts[v * 3 + 1] = world[o + 4] * x + world[o + 5] * y + world[o + 6] * z + world[o + 7];
                verts[v * 3 + 2] = world[o + 8] * x + world[o + 9] * y + world[o + 10] * z + world[o + 11];
                if (normals != null) {
                    float nx = baseNormals[v * 3], ny = baseNormals[v * 3 + 1], nz = baseNormals[v * 3 + 2];
                    normals[v * 3] = world[o] * nx + world[o + 1] * ny + world[o + 2] * nz;
                    normals[v * 3 + 1] = world[o + 4] * nx + world[o + 5] * ny + world[o + 6] * nz;
                    normals[v * 3 + 2] = world[o + 8] * nx + world[o + 9] * ny + world[o + 10] * nz;
                }
            }
        }
        for (; v < numVertices; v++) {
            verts[v * 3] = baseVerts[v * 3];
            verts[v * 3 + 1] = baseVerts[v * 3 + 1];
            verts[v * 3 + 2] = baseVerts[v * 3 + 2];
        }
    }

    /** out[oo..] = a[ao..] * b[bo..] for 3x4 affine matrices (out may alias a). */
    static void mul(float[] a, int ao, float[] b, int bo, float[] out, int oo) {
        float[] r = new float[12];
        for (int row = 0; row < 3; row++) {
            int ar = ao + row * 4;
            for (int c = 0; c < 3; c++) r[row * 4 + c] = a[ar] * b[bo + c] + a[ar + 1] * b[bo + 4 + c] + a[ar + 2] * b[bo + 8 + c];
            r[row * 4 + 3] = a[ar] * b[bo + 3] + a[ar + 1] * b[bo + 7] + a[ar + 2] * b[bo + 11] + a[ar + 3];
        }
        System.arraycopy(r, 0, out, oo, 12);
    }

    /* ---------------- MBAC parsing ---------------- */
    private byte[] d;
    private int p;
    private int bitBuf, bitCount;

    private int u8() { return d[p++] & 255; }

    private int u16() {
        int v = (d[p] & 255) | ((d[p + 1] & 255) << 8);
        p += 2;
        return v;
    }

    private int s16() { return (short) u16(); }

    private int bits(int n) {
        while (bitCount < n) {
            bitBuf |= (d[p++] & 255) << bitCount;
            bitCount += 8;
        }
        int v = bitBuf & ((1 << n) - 1);
        bitBuf >>>= n;
        bitCount -= n;
        return v;
    }

    private int sbits(int n) {
        int v = bits(n);
        return (v << (32 - n)) >> (32 - n);
    }

    private void align() {
        bitBuf = 0;
        bitCount = 0;
    }

    private void parse(byte[] b) {
        d = b;
        p = 0;
        if (u8() != 'M' || u8() != 'B') throw new RuntimeException("Not an MBAC file");
        int version = u8();
        u8();
        if (version < 2 || version > 5) throw new RuntimeException("Unsupported MBAC version " + version);
        int vf = 1, nf = 0, pf = 1;
        if (version > 3) {
            vf = u8();
            nf = u8();
            pf = u8();
            u8(); /* bone format */
        }
        numVertices = u16();
        int numT3 = u16(), numT4 = u16();
        numBones = u16();
        int numC3 = 0, numC4 = 0, numColors = 0;
        if (pf >= 3) {
            numC3 = u16();
            numC4 = u16();
            numTextures = u16();
            numPatterns = u16();
            numColors = u16();
        }
        if (numTextures < 1) numTextures = 1;
        if (numPatterns < 1) numPatterns = 1;
        /* per pattern: colored tri/quad counts, then per texture textured tri/quad counts */
        int[] pc3 = new int[numPatterns], pc4 = new int[numPatterns];
        int[][] pt3 = new int[numPatterns][numTextures], pt4 = new int[numPatterns][numTextures];
        if (version == 5) {
            for (int i = 0; i < numPatterns; i++) {
                pc3[i] = u16();
                pc4[i] = u16();
                for (int j = 0; j < numTextures; j++) {
                    pt3[i][j] = u16();
                    pt4[i][j] = u16();
                }
            }
        } else {
            pc3[0] = numC3;
            pc4[0] = numC4;
            pt3[0][0] = numT3;
            pt4[0][0] = numT4;
        }

        /* vertices */
        baseVerts = new float[numVertices * 3];
        if (vf == 1) {
            for (int i = 0; i < numVertices * 3; i++) baseVerts[i] = s16();
        } else if (vf == 2) {
            int i = 0;
            while (i < numVertices) {
                int h = bits(8);
                int width = new int[] {8, 10, 13, 16}[h >> 6];
                int cnt = (h & 0x3F) + 1;
                for (int k = 0; k < cnt && i < numVertices; k++, i++) {
                    baseVerts[i * 3] = sbits(width);
                    baseVerts[i * 3 + 1] = sbits(width);
                    baseVerts[i * 3 + 2] = sbits(width);
                }
            }
        } else throw new RuntimeException("Unsupported vertex format " + vf);
        align();

        /* normals */
        if (nf != 0) {
            baseNormals = new float[numVertices * 3];
            for (int i = 0; i < numVertices; i++) {
                if (nf == 1) {
                    baseNormals[i * 3] = s16();
                    baseNormals[i * 3 + 1] = s16();
                    baseNormals[i * 3 + 2] = s16();
                    continue;
                }
                int x = bits(7);
                if (x == 64) {
                    int t = bits(3);
                    int[] tbl = {0, 0, 4096, 0, 0, -4096, 0, 0};
                    if (t > 5) throw new RuntimeException("Bad normal");
                    baseNormals[i * 3 + 2] = tbl[t];
                    baseNormals[i * 3 + 1] = tbl[t + 1];
                    baseNormals[i * 3] = tbl[t + 2];
                } else {
                    int nx = ((x << 25) >> 19);
                    int ny = ((bits(7) << 25) >> 19);
                    int sign = bits(1);
                    double zz = 4096.0 * 4096.0 - (double) nx * nx - (double) ny * ny;
                    int nz = zz > 0 ? (int) Math.floor(Math.sqrt(zz) + 0.5) : 0;
                    if (sign == 1) nz = -nz;
                    baseNormals[i * 3] = nx;
                    baseNormals[i * 3 + 1] = ny;
                    baseNormals[i * 3 + 2] = nz;
                }
            }
        }
        align();

        /* colored polygons */
        int totalC = numC3 + numC4 * 2;
        numCol = 0;
        cIdx = new int[totalC * 3];
        cMat = new int[totalC];
        cColor = new int[totalC];
        cPat = new int[totalC];
        int[] c3Mat = new int[numC3], c3Idx = new int[numC3 * 3], c3Col = new int[numC3];
        int[] c4Mat = new int[numC4], c4Idx = new int[numC4 * 4], c4Col = new int[numC4];
        if (numC3 + numC4 > 0) {
            int matBits = u8(), idxBits = u8(), colBits = u8(), colIdBits = u8();
            u8();
            int[] palette = new int[Math.max(1, numColors)];
            for (int i = 0; i < numColors; i++) {
                int r = bits(colBits), g = bits(colBits), bl = bits(colBits);
                palette[i] = ((r & 255) << 16) | ((g & 255) << 8) | (bl & 255);
            }
            for (int i = 0; i < numC3; i++) {
                c3Mat[i] = bits(matBits) << 1;
                for (int k = 0; k < 3; k++) c3Idx[i * 3 + k] = bits(idxBits);
                c3Col[i] = palette[Math.min(bits(colIdBits), palette.length - 1)];
            }
            for (int i = 0; i < numC4; i++) {
                c4Mat[i] = bits(matBits) << 1;
                for (int k = 0; k < 4; k++) c4Idx[i * 4 + k] = bits(idxBits);
                c4Col[i] = palette[Math.min(bits(colIdBits), palette.length - 1)];
            }
        }

        /* textured polygons */
        int[] t3Mat = new int[numT3], t3Idx = new int[numT3 * 3], t3UV = new int[numT3 * 6];
        int[] t4Mat = new int[numT4], t4Idx = new int[numT4 * 4], t4UV = new int[numT4 * 8];
        if (pf == 1) {
            for (int i = 0; i < numT3; i++) {
                int m = u16();
                t3Mat[i] = ((m & 4) << 2) | ((m & 2) >> 1) | M_LIGHTING;
                for (int k = 0; k < 3; k++) t3Idx[i * 3 + k] = u16();
                for (int k = 0; k < 6; k++) t3UV[i * 6 + k] = u8();
            }
            for (int i = 0; i < numT4; i++) {
                int m = u16();
                t4Mat[i] = ((m & 4) << 2) | ((m & 2) >> 1) | M_LIGHTING;
                for (int k = 0; k < 4; k++) t4Idx[i * 4 + k] = u16();
                for (int k = 0; k < 8; k++) t4UV[i * 8 + k] = u8();
            }
        } else if (numT3 + numT4 > 0) {
            int matBits, idxBits, uvBits;
            if (pf == 2) {
                matBits = u8();
                idxBits = u8();
                uvBits = 7;
            } else {
                matBits = bits(8);
                idxBits = bits(8);
                uvBits = bits(8);
                bits(8);
            }
            for (int i = 0; i < numT3; i++) {
                t3Mat[i] = bits(matBits);
                for (int k = 0; k < 3; k++) t3Idx[i * 3 + k] = bits(idxBits);
                for (int k = 0; k < 6; k++) t3UV[i * 6 + k] = bits(uvBits) & 255;
            }
            for (int i = 0; i < numT4; i++) {
                t4Mat[i] = bits(matBits);
                for (int k = 0; k < 4; k++) t4Idx[i * 4 + k] = bits(idxBits);
                for (int k = 0; k < 8; k++) t4UV[i * 8 + k] = bits(uvBits) & 255;
            }
        }
        align();

        /* bones */
        boneLen = new int[numBones];
        boneParent = new int[numBones];
        boneMat = new float[numBones * 12];
        for (int i = 0; i < numBones; i++) {
            boneLen[i] = u16();
            boneParent[i] = s16();
            for (int k = 0; k < 12; k++) {
                int v = s16();
                boneMat[i * 12 + k] = (k % 4 == 3) ? v : v / 4096f;
            }
        }

        /* expand quads and assign pattern/texture by the per-pattern counts */
        int totalT = numT3 + numT4 * 2;
        tIdx = new int[totalT * 3];
        tUV = new int[totalT * 6];
        tMat = new int[totalT];
        tTex = new int[totalT];
        tPat = new int[totalT];
        numTex = 0;
        int ti3 = 0, ti4 = 0, ci3 = 0, ci4 = 0;
        for (int pi = 0; pi < numPatterns; pi++) {
            int pat = pi == 0 ? 0 : 1 << pi;
            for (int k = 0; k < pc3[pi] && ci3 < numC3; k++, ci3++)
                addCol(c3Idx[ci3 * 3], c3Idx[ci3 * 3 + 1], c3Idx[ci3 * 3 + 2], c3Mat[ci3], c3Col[ci3], pat);
            for (int k = 0; k < pc4[pi] && ci4 < numC4; k++, ci4++) {
                int a = c4Idx[ci4 * 4], bb = c4Idx[ci4 * 4 + 1], c = c4Idx[ci4 * 4 + 2], dd = c4Idx[ci4 * 4 + 3];
                addCol(a, bb, c, c4Mat[ci4], c4Col[ci4], pat);
                addCol(c, bb, dd, c4Mat[ci4], c4Col[ci4], pat);
            }
            for (int tex = 0; tex < numTextures; tex++) {
                for (int k = 0; k < pt3[pi][tex] && ti3 < numT3; k++, ti3++) {
                    int o = ti3 * 6;
                    addTex(t3Idx[ti3 * 3], t3Idx[ti3 * 3 + 1], t3Idx[ti3 * 3 + 2], t3UV[o], t3UV[o + 1], t3UV[o + 2], t3UV[o + 3], t3UV[o + 4],
                           t3UV[o + 5], t3Mat[ti3], tex, pat);
                }
                for (int k = 0; k < pt4[pi][tex] && ti4 < numT4; k++, ti4++) {
                    int a = t4Idx[ti4 * 4], bb = t4Idx[ti4 * 4 + 1], c = t4Idx[ti4 * 4 + 2], dd = t4Idx[ti4 * 4 + 3];
                    int o = ti4 * 8;
                    addTex(a, bb, c, t4UV[o], t4UV[o + 1], t4UV[o + 2], t4UV[o + 3], t4UV[o + 4], t4UV[o + 5], t4Mat[ti4], tex, pat);
                    addTex(c, bb, dd, t4UV[o + 4], t4UV[o + 5], t4UV[o + 2], t4UV[o + 3], t4UV[o + 6], t4UV[o + 7], t4Mat[ti4], tex, pat);
                }
            }
        }
        d = null;
    }

    private boolean validIdx(int a, int b, int c) {
        return a >= 0 && b >= 0 && c >= 0 && a < numVertices && b < numVertices && c < numVertices;
    }

    private void addCol(int a, int b, int c, int mat, int color, int pat) {
        if (!validIdx(a, b, c)) return;
        cIdx[numCol * 3] = a;
        cIdx[numCol * 3 + 1] = b;
        cIdx[numCol * 3 + 2] = c;
        cMat[numCol] = mat;
        cColor[numCol] = color;
        cPat[numCol] = pat;
        numCol++;
    }

    private void addTex(int a, int b, int c, int ua, int va, int ub, int vb, int uc, int vc, int mat, int tex, int pat) {
        if (!validIdx(a, b, c)) return;
        int i = numTex;
        tIdx[i * 3] = a;
        tIdx[i * 3 + 1] = b;
        tIdx[i * 3 + 2] = c;
        tUV[i * 6] = ua;
        tUV[i * 6 + 1] = va;
        tUV[i * 6 + 2] = ub;
        tUV[i * 6 + 3] = vb;
        tUV[i * 6 + 4] = uc;
        tUV[i * 6 + 5] = vc;
        tMat[i] = mat;
        tTex[i] = tex;
        tPat[i] = pat;
        numTex++;
    }

    boolean visible(int pat) { return (pat & patternMask) == pat; }
}
