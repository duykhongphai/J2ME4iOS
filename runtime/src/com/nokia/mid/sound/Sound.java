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

    public Sound(byte[] data, int type) { init(data, type); }

    public Sound(int freq, long duration) { init(freq, duration); }

    public static int getConcurrentSoundCount(int type) { return type == FORMAT_WAV ? 4 : 1; }

    public static int[] getSupportedFormats() { return new int[] {FORMAT_TONE, FORMAT_WAV}; }

    public void init(byte[] data, int type) {
        release();
        if (data == null) throw new NullPointerException();
        byte[] wav;
        if (type == FORMAT_WAV) wav = data;
        else if (type == FORMAT_TONE) wav = j4i.NokiaTone.toWav(data);
        else throw new IllegalArgumentException("Unsupported format " + type);
        if (wav != null) {
            handle = j4i.Sys.audioCreate(wav, "audio/x-wav");
            if (handle > 0) j4i.Sys.audioVolume(handle, gain * 100 / 255);
        }
        state = SOUND_STOPPED;
    }

    public void init(int freq, long duration) {
        release();
        if (freq < 0 || duration <= 0) throw new IllegalArgumentException();
        int d = (int) Math.min(duration, 60000);
        byte[] wav = j4i.Tones.wav(new int[] {freq}, new int[] {d}, new int[] {100}, 1);
        handle = j4i.Sys.audioCreate(wav, "audio/x-wav");
        if (handle > 0) j4i.Sys.audioVolume(handle, gain * 100 / 255);
        state = SOUND_STOPPED;
    }

    public void play(int loop) {
        if (state == SOUND_UNINITIALIZED) throw new IllegalStateException();
        if (loop < 0) throw new IllegalArgumentException();
        if (handle > 0) {
            j4i.Sys.audioStop(handle);
            j4i.Sys.audioPlay(handle, loop == 0 ? -1 : loop);
        }
        setState(SOUND_PLAYING);
    }

    public void stop() {
        if (handle > 0) j4i.Sys.audioStop(handle);
        if (state == SOUND_PLAYING) setState(SOUND_STOPPED);
    }

    public void resume() {
        if (state == SOUND_STOPPED) play(1);
    }

    public void release() {
        if (handle > 0) {
            j4i.Sys.audioStop(handle);
            j4i.Sys.audioClose(handle);
            handle = 0;
        }
        state = SOUND_UNINITIALIZED;
    }

    private void setState(int s) {
        state = s;
        if (listener != null) listener.soundStateChanged(this, s);
    }

    public int getState() {
        if (state == SOUND_PLAYING && (handle <= 0 || !j4i.Sys.audioIsPlaying(handle))) setState(SOUND_STOPPED);
        return state;
    }

    public void setGain(int gain) {
        this.gain = Math.max(0, Math.min(255, gain));
        if (handle > 0) j4i.Sys.audioVolume(handle, this.gain * 100 / 255);
    }

    public int getGain() { return gain; }

    public void setSoundListener(SoundListener l) { listener = l; }
}
