package com.samsung.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

/** Samsung sound clip API, mapped onto MMAPI players. */
public class AudioClip {
    public static final int TYPE_MMF = 1;
    public static final int TYPE_MP3 = 2;
    public static final int TYPE_MIDI = 3;

    private Player player;
    private boolean paused;

    public AudioClip(int type, String filename) throws IOException {
        if (filename == null) throw new NullPointerException();
        String path = filename.startsWith("/") ? filename.substring(1) : filename;
        byte[] data = j4i.Sys.getResource(path);
        if (data == null) throw new IOException("Resource not found: " + filename);
        init(type, data, 0, data.length);
    }

    public AudioClip(int type, byte[] audioData, int audioOffset, int audioLength) {
        if (audioData == null) throw new NullPointerException();
        if (audioOffset < 0 || audioLength < 0 || audioOffset + audioLength > audioData.length)
            throw new ArrayIndexOutOfBoundsException();
        init(type, audioData, audioOffset, audioLength);
    }

    private void init(int type, byte[] d, int off, int len) {
        try {
            player = Manager.createPlayer(new ByteArrayInputStream(d, off, len), mime(type, d, off, len));
            player.realize();
        } catch (Exception e) {
            player = null;
        }
    }

    static String mime(int type, byte[] d, int off, int len) {
        if (len >= 4) {
            byte a = d[off], b = d[off + 1], c = d[off + 2], e = d[off + 3];
            if (a == 'M' && b == 'T' && c == 'h' && e == 'd') return "audio/midi";
            if (a == 'R' && b == 'I' && c == 'F' && e == 'F') return "audio/x-wav";
            if (a == 'M' && b == 'M' && c == 'M' && e == 'D') return "application/vnd.smaf";
            if (a == '#' && b == '!' && c == 'A' && e == 'M') return "audio/amr";
            if ((a == 'I' && b == 'D' && c == '3') || ((a & 0xFF) == 0xFF && (b & 0xE0) == 0xE0)) return "audio/mpeg";
        }
        switch (type) {
        case TYPE_MMF: return "application/vnd.smaf";
        case TYPE_MP3: return "audio/mpeg";
        default: return "audio/midi";
        }
    }

    public static boolean isSupported() { return true; }

    /** loop: 0 = endless, otherwise play count (1..255). volume: 0..5. */
    public void play(int loop, int volume) {
        if (loop < 0 || loop > 255 || volume < 0) throw new IllegalArgumentException();
        if (player == null) return;
        try {
            if (player.getState() == Player.STARTED) player.stop();
            player.setMediaTime(0);
            player.setLoopCount(loop == 0 ? -1 : loop);
            VolumeControl vc = (VolumeControl) player.getControl("VolumeControl");
            if (vc != null) vc.setLevel(volume <= 5 ? volume * 20 : Math.min(100, volume));
            paused = false;
            player.start();
        } catch (Exception e) {
        }
    }

    public void pause() {
        if (player == null) return;
        try {
            if (player.getState() == Player.STARTED) {
                player.stop();
                paused = true;
            }
        } catch (Exception e) {
        }
    }

    public void resume() {
        if (player == null || !paused) return;
        try {
            paused = false;
            player.start();
        } catch (Exception e) {
        }
    }

    public void stop() {
        if (player == null) return;
        try {
            paused = false;
            player.stop();
            player.setMediaTime(0);
        } catch (Exception e) {
        }
    }
}
