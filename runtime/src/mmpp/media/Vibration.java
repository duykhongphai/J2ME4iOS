package mmpp.media;

/** LG vibration control. timeout is in milliseconds. */
public class Vibration {
    private Vibration() {}

    public static int getLevelNum() { return 1; }

    public static void start(int level, int timeout) {
        if (timeout > 0) j4i.Sys.vibrate(Math.min(timeout, 5000));
    }

    public static void stop() {}
}
