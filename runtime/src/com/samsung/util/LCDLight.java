package com.samsung.util;

/** Samsung backlight control. The host keeps the screen on while a game runs, so this is a no-op. */
public class LCDLight {
    private LCDLight() {}

    public static void on(int duration) {}

    public static void off() {}

    public static boolean isSupported() { return true; }
}
