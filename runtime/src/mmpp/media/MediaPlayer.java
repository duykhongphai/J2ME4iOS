package mmpp.media;

import java.io.ByteArrayInputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

/** LG media player (MMF/MIDI/WAV/MP3 sounds) mapped onto MMAPI players. Volume levels are 0..5. */
public class MediaPlayer {
    private static final int MAX_VOLUME = 5;

    private Player player;
    private boolean loop;
    private int volume = MAX_VOLUME;
    private boolean paused;

    public MediaPlayer() {}

    private void release() {
        if (player != null) {
            player.close();
            player = null;
        }
        paused = false;
    }

    private void load(byte[] d, int off, int len) {
        release();
        if (d == null) return;
        try {
            Player p = Manager.createPlayer(new ByteArrayInputStream(d, off, len), mime(d, off, len));
            p.realize();
            player = p;
            applyVolume();
        } catch (Exception e) {
            player = null;
        }
    }

    private static String mime(byte[] d, int off, int len) {
        if (len >= 4) {
            byte a = d[off], b = d[off + 1], c = d[off + 2], e = d[off + 3];
            if (a == 'R' && b == 'I' && c == 'F' && e == 'F') return "audio/x-wav";
            if (a == 'M' && b == 'M' && c == 'M' && e == 'D') return "application/vnd.smaf";
            if (a == '#' && b == '!' && c == 'A' && e == 'M') return "audio/amr";
            if ((a == 'I' && b == 'D' && c == '3') || ((a & 0xFF) == 0xFF && (b & 0xE0) == 0xE0)) return "audio/mpeg";
        }
        return "audio/midi";
    }

    private void applyVolume() {
        if (player == null) return;
        VolumeControl vc = (VolumeControl) player.getControl("VolumeControl");
        if (vc != null) vc.setLevel(volume * 100 / MAX_VOLUME);
    }

    /** location is a jar resource path. */
    public void setMediaLocation(String location) {
        if (location == null) throw new NullPointerException();
        String path = location;
        if (path.startsWith("resource:")) path = path.substring(9);
        while (path.startsWith("/")) path = path.substring(1);
        byte[] d = j4i.Sys.getResource(path);
        if (d == null) release();
        else load(d, 0, d.length);
    }

    public void setMediaSource(byte[] buffer, int offset, int length) {
        if (buffer == null) throw new NullPointerException();
        if (offset < 0 || length < 0 || offset + length > buffer.length) throw new ArrayIndexOutOfBoundsException();
        load(buffer, offset, length);
    }

    public void setVolumeLevel(String level) {
        try {
            setVolume(Integer.parseInt(level.trim()));
        } catch (Exception e) {
        }
    }

    public String getVolumeLevel() { return String.valueOf(volume); }

    public void setVolume(int level) {
        volume = Math.max(0, Math.min(MAX_VOLUME, level));
        applyVolume();
    }

    public int getVolume() { return volume; }

    public void start() {
        if (player == null) return;
        try {
            if (player.getState() == Player.STARTED) player.stop();
            player.setMediaTime(0);
            player.setLoopCount(loop ? -1 : 1);
            paused = false;
            player.start();
        } catch (Exception e) {
        }
    }

    public void play() { start(); }

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

    public boolean isPlaying() { return player != null && player.getState() == Player.STARTED; }

    public void setPlayBackLoop(boolean val) { loop = val; }

    public boolean getPlayBackLoop() { return loop; }
}
