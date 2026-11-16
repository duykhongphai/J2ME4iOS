package com.siemens.mp.game;

import j4i.Sys;
import j4i.Tones;

/** A monophonic melody built with MelodyComposer. Only one melody plays at a time. */
public class Melody extends com.siemens.mp.misc.NativeMem {
    private static final Object LOCK = new Object();
    private static int playing;

    private final int[] hz, ms;
    private final int count;
    private final boolean loop;
    private byte[] wav;

    Melody(int[] hz, int[] ms, int count, boolean loop) {
        this.hz = hz;
        this.ms = ms;
        this.count = count;
        this.loop = loop;
    }

    public void play() {
        stop();
        if (count <= 0) return;
        if (wav == null) {
            int[] vol = new int[count];
            for (int i = 0; i < count; i++) vol[i] = 100;
            wav = Tones.wav(hz, ms, vol, count);
        }
        if (wav == null) return;
        int h = Sys.audioCreate(wav, "audio/x-wav");
        if (h <= 0) return;
        Sys.audioVolume(h, 100);
        Sys.audioPlay(h, loop ? -1 : 1);
        synchronized (LOCK) {
            playing = h;
        }
    }

    public static void stop() {
        int h;
        synchronized (LOCK) {
            h = playing;
            playing = 0;
        }
        if (h > 0) {
            Sys.audioStop(h);
            Sys.audioClose(h);
        }
    }
}
