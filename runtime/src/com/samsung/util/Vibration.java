package com.samsung.util;

/** Samsung vibration control. */
public class Vibration {
    private Vibration() {}

    /**
     * duration is documented in seconds; values above 10 are taken as milliseconds since
     * some games pass ms. strength (1..5) is ignored.
     */
    public static void start(int duration, int strength) {
        if (duration <= 0) return;
        int ms = duration <= 10 ? duration * 1000 : duration;
        j4i.Sys.vibrate(Math.min(ms, 5000));
    }

    public static void stop() {}

    public static boolean isSupported() { return true; }
}
