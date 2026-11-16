package com.nokia.mid.ui;

public class DeviceControl {
    private DeviceControl() {}

    public static void setLights(int num, int level) {}

    public static void flashLights(long duration) {}

    public static void startVibra(int freq, long duration) { j4i.Sys.vibrate((int) Math.min(duration, 5000)); }

    public static void stopVibra() {}
}
