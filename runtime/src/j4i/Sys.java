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

    /** Renders a monophonic tone list to a WAV file. freq in Hz (0 = rest), ms, volume 0..100. */
    public static native byte[] synth(int[] freqHz, int[] durMs, int[] vol, int count);

    /* virtual file system (JSR-75 and vendor file APIs). Paths are absolute, "/" separated,
     * e.g. "/root1/save/a.dat"; the VM maps them below the per-game files directory. */
    public static native int fsType(String path); /* 0 missing, 1 file, 2 directory */

    public static native long fsSize(String path);

    public static native long fsModified(String path);

    public static native String[] fsList(String path); /* directories end with "/" */

    public static native byte[] fsRead(String path);

    public static native boolean fsWrite(String path, byte[] data, int off, int len, boolean append);

    public static native boolean fsMkdir(String path);

    public static native boolean fsDelete(String path);

    public static native boolean fsRename(String from, String to);

    public static native boolean fsTruncate(String path, long len);

    public static native long fsFree();

    /* TCP sockets. All calls are non-blocking; callers poll and sleep. */
    public static native int netConnect(String host, int port);

    public static native int netState(int h); /* 0 connecting, 1 connected, -1 failed */

    public static native int netRead(int h, byte[] b, int off, int len); /* >0 bytes, 0 none yet, -1 EOF, -2 error */

    public static native int netWrite(int h, byte[] b, int off, int len); /* bytes written or -1 */

    public static native int netAvailable(int h);

    public static native void netClose(int h);

    public static native String netAddress(int h, boolean local); /* "host:port" */

    /** zlib (RFC1950) or raw deflate decompression; returns null on error. */
    public static native byte[] inflate(byte[] data, int off, int len, int expectedLen);
}
