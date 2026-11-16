package com.siemens.mp.game;

import j4i.Tones;

/** Siemens single tone playback. */
public class Sound {
    private Sound() {}

    /** freq in Hz, time in ms. Returns immediately; the tone plays asynchronously. */
    public static void playTone(int freq, int time) {
        if (freq <= 0 || time <= 0) return;
        byte[] wav = Tones.wav(new int[] {freq}, new int[] {time}, new int[] {100}, 1);
        Tones.playOnce(wav, 100);
    }
}
