package j4i;

/** Nokia Smart Messaging ringing tones (OTT / FORMAT_TONE) rendered to WAV. */
public final class NokiaTone {
    private NokiaTone() {}

    private static final int[] BPM = {25, 28, 31, 35, 40, 45, 50, 56, 63, 70, 80, 90, 100, 112, 125, 140, 160, 180, 200, 225, 250, 285, 320,
        355, 400, 450, 500, 565, 635, 715, 800, 900};

    private static byte[] d;
    private static int bit;
    private static int[] hz, ms, vol;
    private static int n;

    private static int bits(int count) {
        int v = 0;
        for (int i = 0; i < count; i++) {
            int byteI = bit >> 3;
            if (byteI >= d.length) throw new IllegalArgumentException("Truncated tone");
            v = (v << 1) | ((d[byteI] >> (7 - (bit & 7))) & 1);
            bit++;
        }
        return v;
    }

    private static void align() { bit = (bit + 7) & ~7; }

    private static void add(int f, int t, int v) {
        if (n == hz.length) {
            int[] a = new int[n * 2], b = new int[n * 2], c = new int[n * 2];
            System.arraycopy(hz, 0, a, 0, n);
            System.arraycopy(ms, 0, b, 0, n);
            System.arraycopy(vol, 0, c, 0, n);
            hz = a;
            ms = b;
            vol = c;
        }
        hz[n] = f;
        ms[n] = t;
        vol[n] = v;
        n++;
    }

    /** Returns WAV bytes, or null if the data is not a recognizable ringing tone. */
    public static synchronized byte[] toWav(byte[] data) {
        try {
            return parse(data);
        } catch (RuntimeException e) {
            System.out.println("Nokia tone: " + e);
            return null;
        }
    }

    private static byte[] parse(byte[] data) {
        d = data;
        bit = 0;
        hz = new int[64];
        ms = new int[64];
        vol = new int[64];
        n = 0;
        int commands = bits(8);
        boolean sound = false;
        for (int c = 0; c < commands && !sound; c++) {
            int cmd = bits(7);
            if (cmd == 0x25) { /* ringing tone programming */
                align();
            } else if (cmd == 0x1D) { /* sound */
                sound = true;
            } else if (cmd == 0x22) { /* unicode */
                align();
            } else if (cmd == 0x0A) { /* cancel */
                return null;
            } else {
                align();
            }
        }
        if (!sound) throw new IllegalArgumentException("No sound command");
        int songType = bits(3);
        if (songType == 1) { /* basic song */
            int titleLen = bits(4);
            for (int i = 0; i < titleLen; i++) bits(8);
        } else if (songType != 2) throw new IllegalArgumentException("Unsupported song type " + songType);
        int patterns = bits(8);
        int scaleBase = 72; /* scale-2: A = 880 Hz */
        int style = 0;
        int bpm = 63;
        int volume = 7;
        int[][] stored = new int[4][];
        int[] tmpH = new int[0];
        for (int pi = 0; pi < patterns; pi++) {
            bits(3); /* pattern header id */
            int pid = bits(2);
            int loop = bits(4);
            int count = bits(8);
            int start = n;
            if (count == 0 && stored[pid] != null) {
                int[] p = stored[pid];
                for (int k = 0; k < p.length; k += 3) add(p[k], p[k + 1], p[k + 2]);
            }
            for (int ii = 0; ii < count; ii++) {
                int id = bits(3);
                switch (id) {
                    case 1: { /* note */
                        int note = bits(4);
                        int dur = bits(3);
                        int spec = bits(2);
                        double beats = 4.0 / (1 << Math.min(dur, 5));
                        if (spec == 1) beats *= 1.5;
                        else if (spec == 2) beats *= 1.75;
                        else if (spec == 3) beats *= 2.0 / 3;
                        int t = (int) (beats * 60000 / bpm + 0.5);
                        int f = note == 0 || note > 12 ? 0 : Tones.noteHz(scaleBase + note - 1);
                        int v = volume * 100 / 15;
                        if (f > 0 && style != 1) {
                            int gap = style == 2 ? t / 2 : t / 16;
                            add(f, t - gap, v);
                            add(0, gap, 0);
                        } else add(f, t, v);
                        break;
                    }
                    case 2: scaleBase = 60 + 12 * bits(2); break;
                    case 3: style = bits(2); break;
                    case 4: bpm = BPM[bits(5)]; break;
                    case 5: volume = bits(4); break;
                    default: throw new IllegalArgumentException("Bad instruction " + id);
                }
            }
            int len = n - start;
            int[] p = new int[len * 3];
            for (int k = 0; k < len; k++) {
                p[k * 3] = hz[start + k];
                p[k * 3 + 1] = ms[start + k];
                p[k * 3 + 2] = vol[start + k];
            }
            if (count > 0) stored[pid] = p;
            int reps = loop == 15 ? 1 : loop;
            for (int r = 0; r < reps; r++)
                for (int k = 0; k < len; k++) add(p[k * 3], p[k * 3 + 1], p[k * 3 + 2]);
        }
        if (n == 0) return null;
        return Tones.wav(hz, ms, vol, n);
    }
}
