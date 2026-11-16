package j4i;

import java.util.Vector;

/** Simple tone synthesis helpers shared by MMAPI ToneControl and vendor sound APIs. */
public final class Tones {
    private Tones() {}

    private static final Vector playing = new Vector();

    /** Frequency in Hz of a MIDI note (69 = A4 = 440 Hz). */
    public static int noteHz(int note) {
        if (note < 0) return 0;
        return (int) (440.0 * Math.pow(2.0, (note - 69) / 12.0) + 0.5);
    }

    /** MIDI note closest to a frequency. */
    public static int hzNote(int hz) {
        if (hz <= 0) return -1;
        int n = (int) Math.floor(69 + 12 * Math.log(hz / 440.0) / Math.log(2) + 0.5);
        return n < 0 ? 0 : (n > 127 ? 127 : n);
    }

    public static byte[] wav(int[] hz, int[] ms, int[] vol, int count) { return Sys.synth(hz, ms, vol, count); }

    /** Plays a WAV once without a Player object; finished clips are released lazily. */
    public static int playOnce(byte[] wav, int volume) {
        reap();
        if (wav == null) return 0;
        int h = Sys.audioCreate(wav, "audio/x-wav");
        if (h <= 0) return 0;
        Sys.audioVolume(h, volume);
        Sys.audioPlay(h, 1);
        synchronized (playing) {
            playing.addElement(new Integer(h));
        }
        return h;
    }

    public static void stop(int h) {
        if (h <= 0) return;
        Sys.audioStop(h);
    }

    private static void reap() {
        synchronized (playing) {
            for (int i = playing.size() - 1; i >= 0; i--) {
                int h = ((Integer) playing.elementAt(i)).intValue();
                if (!Sys.audioIsPlaying(h)) {
                    Sys.audioClose(h);
                    playing.removeElementAt(i);
                }
            }
        }
    }
}
