package com.siemens.mp.game;

/** Siemens vibrator control mapped onto the host vibration. */
public class Vibrator {
    private Vibrator() {}

    public static void startVibrator() { j4i.Sys.vibrate(1000); }

    public static void stopVibrator() {}

    public static void triggerVibrator(int duration) {
        if (duration > 0) j4i.Sys.vibrate(Math.min(duration, 5000));
    }
}
