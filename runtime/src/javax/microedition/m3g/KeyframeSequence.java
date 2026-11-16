package javax.microedition.m3g;

public class KeyframeSequence extends Object3D {
    public static final int CONSTANT = 192;
    public static final int LOOP = 193;
    public static final int LINEAR = 176;
    public static final int SLERP = 177;
    public static final int SPLINE = 178;
    public static final int SQUAD = 179;
    public static final int STEP = 180;

    int keyframeCount, componentCount, interpolation;
    int[] times;
    float[] values; /* keyframeCount * componentCount */
    int repeatMode = CONSTANT;
    int duration;
    int validFirst, validLast;

    public KeyframeSequence(int numKeyframes, int numComponents, int interpolation) {
        if (numKeyframes < 1 || numComponents < 1) throw new IllegalArgumentException();
        if (interpolation < LINEAR || interpolation > STEP) throw new IllegalArgumentException("Bad interpolation");
        if ((interpolation == SLERP || interpolation == SQUAD) && numComponents != 4) throw new IllegalArgumentException();
        keyframeCount = numKeyframes;
        componentCount = numComponents;
        this.interpolation = interpolation;
        times = new int[numKeyframes];
        values = new float[numKeyframes * numComponents];
        validFirst = 0;
        validLast = numKeyframes - 1;
    }

    Object3D dup() {
        KeyframeSequence k = new KeyframeSequence(keyframeCount, componentCount, interpolation);
        System.arraycopy(times, 0, k.times, 0, times.length);
        System.arraycopy(values, 0, k.values, 0, values.length);
        k.repeatMode = repeatMode;
        k.duration = duration;
        k.validFirst = validFirst;
        k.validLast = validLast;
        return k;
    }

    public int getComponentCount() { return componentCount; }

    public int getKeyframeCount() { return keyframeCount; }

    public int getInterpolationType() { return interpolation; }

    public void setKeyframe(int index, int time, float[] value) {
        if (value == null) throw new NullPointerException();
        if (index < 0 || index >= keyframeCount) throw new IndexOutOfBoundsException();
        if (time < 0 || value.length < componentCount) throw new IllegalArgumentException();
        times[index] = time;
        System.arraycopy(value, 0, values, index * componentCount, componentCount);
        if (interpolation == SLERP || interpolation == SQUAD) {
            float[] q = new float[4];
            System.arraycopy(value, 0, q, 0, 4);
            Quat.normalize(q);
            System.arraycopy(q, 0, values, index * 4, 4);
        }
    }

    public int getKeyframe(int index, float[] value) {
        if (index < 0 || index >= keyframeCount) throw new IndexOutOfBoundsException();
        if (value != null) {
            if (value.length < componentCount) throw new IllegalArgumentException();
            System.arraycopy(values, index * componentCount, value, 0, componentCount);
        }
        return times[index];
    }

    public void setValidRange(int first, int last) {
        if (first < 0 || first >= keyframeCount || last < 0 || last >= keyframeCount) throw new IndexOutOfBoundsException();
        validFirst = first;
        validLast = last;
    }

    public int getValidRangeFirst() { return validFirst; }

    public int getValidRangeLast() { return validLast; }

    public void setDuration(int duration) {
        if (duration <= 0) throw new IllegalArgumentException();
        this.duration = duration;
    }

    public int getDuration() { return duration; }

    public void setRepeatMode(int mode) {
        if (mode != CONSTANT && mode != LOOP) throw new IllegalArgumentException();
        repeatMode = mode;
    }

    public int getRepeatMode() { return repeatMode; }

    /** Samples the sequence at a (sequence) time into out[0..componentCount). */
    void sample(float t, float[] out) {
        int cc = componentCount;
        /* keyframes in the valid range, in order */
        int n = validLast >= validFirst ? validLast - validFirst + 1 : keyframeCount - validFirst + validLast + 1;
        if (n <= 0) n = 1;
        if (repeatMode == LOOP && duration > 0) {
            t = t % duration;
            if (t < 0) t += duration;
        }
        int first = validFirst;
        int last = (validFirst + n - 1) % keyframeCount;
        if (n == 1) {
            copy(first, out);
            return;
        }
        if (repeatMode != LOOP) {
            if (t <= times[first]) {
                copy(first, out);
                return;
            }
            if (t >= times[last]) {
                copy(last, out);
                return;
            }
        }
        /* find segment k0 -> k1 */
        int k0 = -1, k1 = -1;
        float t0 = 0, t1 = 0;
        for (int i = 0; i < n - 1; i++) {
            int a = (first + i) % keyframeCount, b = (first + i + 1) % keyframeCount;
            if (t >= times[a] && t < times[b]) {
                k0 = a;
                k1 = b;
                t0 = times[a];
                t1 = times[b];
                break;
            }
        }
        if (k0 < 0) { /* looping: wrap from last to first */
            if (repeatMode == LOOP && duration > 0) {
                k0 = last;
                k1 = first;
                t0 = times[last];
                t1 = times[first] + duration;
                if (t < times[first]) t += duration;
            } else {
                copy(t < times[first] ? first : last, out);
                return;
            }
        }
        if (interpolation == STEP || t1 <= t0) {
            copy(k0, out);
            return;
        }
        float s = (t - t0) / (t1 - t0);
        if (interpolation == SLERP || interpolation == SQUAD) {
            Quat.slerp(values, k0 * 4, values, k1 * 4, s, out);
            return;
        }
        if (interpolation == SPLINE && n > 2) {
            /* Catmull-Rom style tangents from the neighbouring keyframes */
            int km = prevKey(k0, first, last, n), kp = nextKey(k1, first, last, n);
            float s2 = s * s, s3 = s2 * s;
            float h00 = 2 * s3 - 3 * s2 + 1, h10 = s3 - 2 * s2 + s, h01 = -2 * s3 + 3 * s2, h11 = s3 - s2;
            for (int c = 0; c < cc; c++) {
                float p0 = values[k0 * cc + c], p1 = values[k1 * cc + c];
                float m0 = km >= 0 ? (p1 - values[km * cc + c]) * 0.5f : p1 - p0;
                float m1 = kp >= 0 ? (values[kp * cc + c] - p0) * 0.5f : p1 - p0;
                out[c] = h00 * p0 + h10 * m0 + h01 * p1 + h11 * m1;
            }
            return;
        }
        for (int c = 0; c < cc; c++) out[c] = values[k0 * cc + c] + (values[k1 * cc + c] - values[k0 * cc + c]) * s;
    }

    private int prevKey(int k, int first, int last, int n) {
        if (k != first) return (k - 1 + keyframeCount) % keyframeCount;
        return repeatMode == LOOP ? last : -1;
    }

    private int nextKey(int k, int first, int last, int n) {
        if (k != last) return (k + 1) % keyframeCount;
        return repeatMode == LOOP ? first : -1;
    }

    private void copy(int k, float[] out) { System.arraycopy(values, k * componentCount, out, 0, componentCount); }
}
