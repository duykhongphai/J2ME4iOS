package com.mascotcapsule.micro3d.v3;

import java.io.IOException;

/** MTRA animation data. Frames are 16.16 fixed point. */
public class ActionTable {
    static final class Track {
        int[] keys;
        float[] values; /* comps per key */
        int comps;
    }

    static final class BoneAnim {
        int type;
        float[] fixed; /* type 0: 12 floats */
        Track translate, scale, rotate, roll;
        float[] constT; /* type 3 */
        float constRoll;
    }

    static final class Action {
        int keyframes;
        BoneAnim[] bones;
        int[] dynFrames, dynMasks;
    }

    Action[] actions;
    int numBones;
    private boolean disposed;

    public ActionTable(byte[] b) {
        if (b == null) throw new NullPointerException();
        try {
            parse(b);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RuntimeException("Truncated MTRA data");
        }
    }

    public ActionTable(String name) throws IOException { this(Figure.readResource(name)); }

    public final void dispose() {
        disposed = true;
        actions = null;
    }

    private void check() {
        if (disposed) throw new IllegalStateException("ActionTable disposed");
    }

    public final int getNumActions() {
        check();
        return actions.length;
    }

    public final int getNumAction() { return getNumActions(); }

    public final int getNumFrames(int idx) {
        check();
        if (idx < 0 || idx >= actions.length) throw new IllegalArgumentException();
        return actions[idx].keyframes << 16;
    }

    public final int getNumFrame(int idx) { return getNumFrames(idx); }

    /* ---- parsing ---- */
    private byte[] d;
    private int p;

    private int u8() { return d[p++] & 255; }

    private int u16() {
        int v = (d[p] & 255) | ((d[p + 1] & 255) << 8);
        p += 2;
        return v;
    }

    private int s16() { return (short) u16(); }

    private int s32() {
        int v = (d[p] & 255) | ((d[p + 1] & 255) << 8) | ((d[p + 2] & 255) << 16) | (d[p + 3] << 24);
        p += 4;
        return v;
    }

    private Track track(int comps) {
        Track t = new Track();
        int n = u16();
        t.comps = comps;
        t.keys = new int[n];
        t.values = new float[n * comps];
        for (int i = 0; i < n; i++) {
            t.keys[i] = u16();
            for (int c = 0; c < comps; c++) t.values[i * comps + c] = s16();
        }
        return t;
    }

    private void parse(byte[] b) {
        d = b;
        p = 0;
        if (u8() != 'M' || u8() != 'T') throw new RuntimeException("Not an MTRA file");
        int version = u8();
        u8();
        if (version < 2 || version > 5) throw new RuntimeException("Unsupported MTRA version " + version);
        int na = u16();
        numBones = u16();
        for (int i = 0; i < 8; i++) u16();
        s32();
        actions = new Action[na];
        for (int a = 0; a < na; a++) {
            Action act = new Action();
            act.keyframes = u16();
            act.bones = new BoneAnim[numBones];
            for (int i = 0; i < numBones; i++) {
                BoneAnim ba = new BoneAnim();
                ba.type = u8();
                switch (ba.type) {
                    case 0:
                        ba.fixed = new float[12];
                        for (int k = 0; k < 12; k++) {
                            int v = s16();
                            ba.fixed[k] = (k % 4 == 3) ? v : v / 4096f;
                        }
                        break;
                    case 1:
                        break;
                    case 2:
                        ba.translate = track(3);
                        ba.scale = track(3);
                        ba.rotate = track(3);
                        ba.roll = track(1);
                        break;
                    case 3:
                        ba.constT = new float[] {s16(), s16(), s16()};
                        ba.rotate = track(3);
                        ba.constRoll = s16();
                        break;
                    case 4:
                        ba.rotate = track(3);
                        ba.roll = track(1);
                        break;
                    case 5:
                        ba.rotate = track(3);
                        break;
                    case 6:
                        ba.translate = track(3);
                        ba.rotate = track(3);
                        ba.roll = track(1);
                        break;
                    default:
                        throw new RuntimeException("Unknown MTRA bone type " + ba.type);
                }
                act.bones[i] = ba;
            }
            if (version >= 5) {
                int n = u16();
                act.dynFrames = new int[n];
                act.dynMasks = new int[n];
                for (int i = 0; i < n; i++) {
                    act.dynFrames[i] = u16();
                    act.dynMasks[i] = s32();
                }
            }
            actions[a] = act;
        }
        d = null;
    }

    /* ---- evaluation ---- */
    /** Samples a track at keyframe time k into out (unchanged before the first key). */
    private static boolean sample(Track t, float k, float[] out) {
        int n = t.keys.length;
        if (n == 0) return false;
        int c = t.comps;
        if (k >= t.keys[n - 1]) {
            System.arraycopy(t.values, (n - 1) * c, out, 0, c);
            return true;
        }
        if (k < t.keys[0]) return false;
        int i = 0;
        while (i + 1 < n && t.keys[i + 1] <= k) i++;
        if (t.keys[i] == k || i + 1 >= n) {
            System.arraycopy(t.values, i * c, out, 0, c);
            return true;
        }
        float s = (k - t.keys[i]) / (float) (t.keys[i + 1] - t.keys[i]);
        for (int j = 0; j < c; j++) out[j] = t.values[i * c + j] + (t.values[(i + 1) * c + j] - t.values[i * c + j]) * s;
        return true;
    }

    /** Fills 12 floats per bone (row-major 3x4) for the action at frame (16.16). */
    void pose(int action, int frame, float[] out) {
        Action act = actions[action];
        float k = frame / 65536f;
        float[] tv = new float[3], sv = new float[3], rv = new float[3], roll = new float[1];
        for (int i = 0; i < act.bones.length && (i + 1) * 12 <= out.length; i++) {
            BoneAnim b = act.bones[i];
            int o = i * 12;
            if (b.type == 0) {
                System.arraycopy(b.fixed, 0, out, o, 12);
                continue;
            }
            if (b.type == 1) {
                for (int j = 0; j < 12; j++) out[o + j] = (j == 0 || j == 5 || j == 10) ? 1 : 0;
                continue;
            }
            tv[0] = tv[1] = tv[2] = 0;
            sv[0] = sv[1] = sv[2] = 4096;
            rv[0] = 0;
            rv[1] = 0;
            rv[2] = 4096;
            roll[0] = 0;
            if (b.translate != null) sample(b.translate, k, tv);
            if (b.constT != null) System.arraycopy(b.constT, 0, tv, 0, 3);
            if (b.scale != null) sample(b.scale, k, sv);
            if (b.rotate != null) sample(b.rotate, k, rv);
            if (b.roll != null && !sample(b.roll, k, roll)) roll[0] = 0;
            if (b.type == 3) roll[0] = b.constRoll;
            /* rotation: Z axis points along rv */
            float x = rv[0], y = rv[1], z = rv[2];
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len == 0) {
                x = 0;
                y = 0;
                z = 1;
            } else {
                x /= len;
                y /= len;
                z /= len;
            }
            float r00, r01, r02, r10, r11, r12, r20, r21, r22;
            float xy2 = x * x + y * y;
            if (xy2 > 0) {
                float a = (1 - z) / xy2, bb = -x * y * a;
                r00 = z + y * y * a;
                r01 = bb;
                r02 = x;
                r10 = bb;
                r11 = z + x * x * a;
                r12 = y;
                r20 = -x;
                r21 = -y;
                r22 = z;
            } else {
                r00 = 1;
                r01 = 0;
                r02 = 0;
                r10 = 0;
                r11 = z;
                r12 = 0;
                r20 = 0;
                r21 = 0;
                r22 = z;
            }
            if (roll[0] != 0) {
                double th = roll[0] * Math.PI / 2048;
                float s = (float) Math.sin(th), c = (float) Math.cos(th);
                float n0, n1;
                n0 = r00 * c + r01 * s;
                n1 = r01 * c - r00 * s;
                r00 = n0;
                r01 = n1;
                n0 = r10 * c + r11 * s;
                n1 = r11 * c - r10 * s;
                r10 = n0;
                r11 = n1;
                n0 = r20 * c + r21 * s;
                n1 = r21 * c - r20 * s;
                r20 = n0;
                r21 = n1;
            }
            if (b.type == 2) {
                float sx = sv[0] / 4096f, sy = sv[1] / 4096f, sz = sv[2] / 4096f;
                r00 *= sx;
                r10 *= sx;
                r20 *= sx;
                r01 *= sy;
                r11 *= sy;
                r21 *= sy;
                r02 *= sz;
                r12 *= sz;
                r22 *= sz;
            }
            out[o] = r00;
            out[o + 1] = r01;
            out[o + 2] = r02;
            out[o + 4] = r10;
            out[o + 5] = r11;
            out[o + 6] = r12;
            out[o + 8] = r20;
            out[o + 9] = r21;
            out[o + 10] = r22;
            if (b.type == 4 || b.type == 5) {
                out[o + 3] = 0;
                out[o + 7] = 0;
                out[o + 11] = 0;
            } else {
                out[o + 3] = tv[0];
                out[o + 7] = tv[1];
                out[o + 11] = tv[2];
            }
        }
    }

    /** Pattern mask from the action's dynamic table at frame (16.16), or -1 if none. */
    int dynamicPattern(int action, int frame) {
        Action act = actions[action];
        if (act.dynFrames == null || act.dynFrames.length == 0) return -1;
        int f = frame >> 16;
        int mask = -1;
        for (int i = 0; i < act.dynFrames.length; i++)
            if (act.dynFrames[i] <= f) mask = act.dynMasks[i];
        return mask;
    }
}
