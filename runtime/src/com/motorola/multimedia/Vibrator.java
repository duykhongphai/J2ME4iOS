package com.motorola.multimedia;

/** Motorola vibrator control mapped onto the host vibration. */
public class Vibrator {
    public static final int VIBRATE_SHORT = 1;
    public static final int VIBRATE_LONG = 2;
    public static final int MAX_VIBRATE_TIME = 5000;
    public static final int MIN_PAUSE_TIME = 100;

    private Vibrator() {}

    private static void buzz(int ms) {
        if (ms > 0) j4i.Sys.vibrate(Math.min(ms, MAX_VIBRATE_TIME));
    }

    public static void vibrateFor(int timeInMs) { buzz(timeInMs); }

    public static void vibratePeriodically(int timeInMs) { buzz(timeInMs); }

    public static void vibratePeriodically(int timeInMs, int timeOffInMs) { buzz(timeInMs); }

    public static void vibratePeriodicallyFor(int timeInMs, int totalTimeInMs) { buzz(timeInMs); }

    public static void setVibrateTone(int tone) {}

    public static void vibrateOn() { buzz(1000); }

    public static void vibrateOff() {}

    public static void stop() {}
}
