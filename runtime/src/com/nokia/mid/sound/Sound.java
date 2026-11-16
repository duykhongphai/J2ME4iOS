package com.nokia.mid.sound;

public class Sound {
    public static final int FORMAT_TONE = 1;
    public static final int FORMAT_WAV = 5;
    public static final int SOUND_PLAYING = 0;
    public static final int SOUND_STOPPED = 1;
    public static final int SOUND_UNINITIALIZED = 3;

    private int handle;
    private int state = SOUND_UNINITIALIZED;
    private int gain = 255;
    private SoundListener listener;
    private int freq;
    private long duration;

    public Sound(byte[] data, int type) { init(data, type); }

    public Sound(int freq, long duration) { init(freq, duration); }

    public static int getConcurrentSoundCount(int type) { return 1; }

    public static int[] getSupportedFormats() { return new int[] {FORMAT_TONE, FORMAT_WAV}; }

    public void init(byte[] data, int type) {
        release();
        if (data == null) throw new NullPointerException();
        if (type == FORMAT_WAV) handle = j4i.Sys.audioCreate(data, "audio/x-wav");
        state = SOUND_STOPPED;
    }

    public void init(int freq, long duration) {
        release();
        this.freq = freq;
        this.duration = duration;
        state = SOUND_STOPPED;
    }

    public void play(int loop) {
        if (state == SOUND_UNINITIALIZED) return;
        if (handle > 0) j4i.Sys.audioPlay(handle, loop == 0 ? -1 : loop);
        else if (freq > 0) {
            int note = (int) Math.floor(69 + 12 * Math.log(freq / 440.0) / Math.log(2) + 0.5);
            j4i.Sys.tone(Math.max(0, Math.min(127, note)), (int) duration, gain * 100 / 255);
        }
        state = SOUND_PLAYING;
        if (listener != null) listener.soundStateChanged(this, SOUND_PLAYING);
    }

    public void stop() {
        if (handle > 0) j4i.Sys.audioStop(handle);
        state = SOUND_STOPPED;
        if (listener != null) listener.soundStateChanged(this, SOUND_STOPPED);
    }

    public void resume() { play(1); }

    public void release() {
        if (handle > 0) {
            j4i.Sys.audioClose(handle);
            handle = 0;
        }
        state = SOUND_UNINITIALIZED;
    }

    public int getState() {
        if (state == SOUND_PLAYING && handle > 0 && !j4i.Sys.audioIsPlaying(handle)) state = SOUND_STOPPED;
        return state;
    }

    public void setGain(int gain) {
        this.gain = Math.max(0, Math.min(255, gain));
        if (handle > 0) j4i.Sys.audioVolume(handle, this.gain * 100 / 255);
    }

    public int getGain() { return gain; }

    public void setSoundListener(SoundListener l) { listener = l; }
}
