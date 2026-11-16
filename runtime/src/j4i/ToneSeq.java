package j4i;

/** Parses MMAPI tone sequences (ToneControl format) and renders them to WAV. */
public final class ToneSeq {
    private ToneSeq() {}

    private static final int VERSION = -2, TEMPO = -3, RESOLUTION = -4, BLOCK_START = -5, BLOCK_END = -6, PLAY_BLOCK = -7,
        SET_VOLUME = -8, REPEAT = -9, SILENCE = -1;

    private static int[] hz, ms, vol;
    private static int n;

    /** Throws IllegalArgumentException for malformed sequences, like the real ToneControl. */
    public static synchronized byte[] toWav(byte[] seq) {
        if (seq == null || seq.length < 2) throw new IllegalArgumentException("Empty tone sequence");
        hz = new int[64];
        ms = new int[64];
        vol = new int[64];
        n = 0;
        int p = 0;
        if (seq[p] != VERSION || seq[p + 1] != 1) throw new IllegalArgumentException("Bad tone sequence version");
        p += 2;
        int tempo = 30, res = 64;
        if (p + 1 < seq.length && seq[p] == TEMPO) {
            tempo = seq[p + 1];
            if (tempo < 5) throw new IllegalArgumentException("Bad tempo");
            p += 2;
        }
        if (p + 1 < seq.length && seq[p] == RESOLUTION) {
            res = seq[p + 1];
            if (res < 1) throw new IllegalArgumentException("Bad resolution");
            p += 2;
        }
        int[] blockStart = new int[128];
        for (int i = 0; i < 128; i++) blockStart[i] = -1;
        while (p + 1 < seq.length && seq[p] == BLOCK_START) {
            int b = seq[p + 1];
            if (b < 0) throw new IllegalArgumentException("Bad block number");
            p += 2;
            blockStart[b] = p;
            while (p + 1 < seq.length && !(seq[p] == BLOCK_END && seq[p + 1] == b)) p += 2;
            if (p + 1 >= seq.length) throw new IllegalArgumentException("Unterminated block " + b);
            p += 2;
        }
        /* bpm = tempo * 4; a duration of `res` units is a whole note (4 beats) */
        double unitMs = 60000.0 * 4 / ((double) res * tempo * 4);
        int[] volume = {100};
        play(seq, p, seq.length, blockStart, unitMs, volume, 0);
        if (n == 0) add(0, 1, 0);
        return Tones.wav(hz, ms, vol, n);
    }

    private static void add(int f, int d, int v) {
        if (n > 0 && hz[n - 1] == f && vol[n - 1] == v && f == 0) {
            ms[n - 1] += d;
            return;
        }
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
        ms[n] = d;
        vol[n] = v;
        n++;
    }

    private static void play(byte[] s, int p, int end, int[] blocks, double unitMs, int[] volume, int depth) {
        if (depth > 16) throw new IllegalArgumentException("Blocks nested too deep");
        while (p + 1 < end) {
            int ev = s[p];
            if (ev == BLOCK_END) return;
            int arg = s[p + 1];
            p += 2;
            if (ev >= 0 || ev == SILENCE) {
                if (arg <= 0) throw new IllegalArgumentException("Bad duration");
                add(ev == SILENCE ? 0 : Tones.noteHz(ev), (int) (arg * unitMs + 0.5), volume[0]);
            } else if (ev == PLAY_BLOCK) {
                if (arg < 0 || blocks[arg] < 0) throw new IllegalArgumentException("Undefined block " + arg);
                play(s, blocks[arg], end, blocks, unitMs, volume, depth + 1);
            } else if (ev == SET_VOLUME) {
                volume[0] = Math.max(0, Math.min(100, arg));
            } else if (ev == REPEAT) {
                if (p + 1 >= end) throw new IllegalArgumentException("REPEAT without note");
                int note = s[p], dur = s[p + 1];
                p += 2;
                if (dur <= 0 || (note < 0 && note != SILENCE)) throw new IllegalArgumentException("Bad REPEAT");
                for (int i = 0; i < arg; i++) add(note == SILENCE ? 0 : Tones.noteHz(note), (int) (dur * unitMs + 0.5), volume[0]);
            } else {
                throw new IllegalArgumentException("Bad tone event " + ev);
            }
        }
    }
}
