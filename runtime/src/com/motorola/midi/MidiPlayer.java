package com.motorola.midi;

import javax.microedition.media.Manager;
import javax.microedition.media.Player;

/** Motorola MIDI file playback. */
public class MidiPlayer {
    public static final int PLAY_CONTINUOUS = 0;
    public static final int PLAY_ONCE = 1;

    private static Player player;

    private MidiPlayer() {}

    public static synchronized void play(String filename, int mode) {
        stop();
        if (filename == null) return;
        String path = filename;
        int scheme = path.indexOf("://");
        if (scheme >= 0) path = path.substring(scheme + 3);
        else if (path.startsWith("resource:")) path = path.substring(9);
        while (path.startsWith("/")) path = path.substring(1);
        try {
            Player p = Manager.createPlayer("resource:/" + path);
            p.realize();
            if (mode == PLAY_CONTINUOUS) p.setLoopCount(-1);
            p.start();
            player = p;
        } catch (Exception e) {
        }
    }

    public static synchronized void stop() {
        if (player != null) {
            player.close();
            player = null;
        }
    }

    public static void playEffect(int note, int key_pressure, int channel_vol, int instrument, int channel) {
        if (note < 0 || note > 127) return;
        try {
            Manager.playTone(note, 200, Math.max(0, Math.min(100, channel_vol * 100 / 127)));
        } catch (Exception e) {
        }
    }
}
