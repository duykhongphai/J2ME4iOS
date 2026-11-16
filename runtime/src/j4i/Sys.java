package j4i;

/** Native bridge to the VM and the host platform. */
public final class Sys {
    private Sys() {}

    public static native void log(String s);

    public static native void logBytes(byte[] b, int len, boolean err);

    public static native byte[] getResource(String path);

    public static native String getProperty(String key);

    public static native String getAppProperty(String key);

    public static native void exit(int code);

    public static native int tzOffset();

    /** Static main(String[]) of cls, used for console test programs. */
    public static native void invokeMain(Class cls, String[] args);

    /* events: ev[0]=type, ev[1], ev[2]. Returns true if an event was received. */
    public static native boolean waitEvent(int[] ev, int timeoutMs);

    public static native void wakeEvents();

    public static native int screenWidth();

    public static native int screenHeight();

    public static native void flush(int[] pixels, int w, int h);

    /* persistent storage (RecordStores) */
    public static native byte[] readFile(String name);

    public static native boolean writeFile(String name, byte[] data);

    public static native boolean deleteFile(String name);

    public static native String[] listFiles();

    /* misc host services */
    public static native void vibrate(int ms);

    public static native boolean platformRequest(String url);

    public static native int requestTextInput(String title, String text, int maxLen, int constraints);

    public static native String takeTextInput(int id);

    /* audio */
    public static native int audioCreate(byte[] data, String mime);

    public static native void audioPlay(int h, int loops);

    public static native void audioStop(int h);

    public static native void audioClose(int h);

    public static native void audioVolume(int h, int level);

    public static native boolean audioIsPlaying(int h);

    public static native void tone(int note, int duration, int volume);
}
