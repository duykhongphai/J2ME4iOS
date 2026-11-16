package com.motorola.multimedia;

/** Motorola backlight / keypad light control. The host keeps the screen lit; all no-ops. */
public class Lighting {
    private Lighting() {}

    public static void backlightOn() {}

    public static void backlightOff() {}

    public static void keypadLightOn() {}

    public static void keypadLightOff() {}

    public static void sideLightOn() {}

    public static void sideLightOff() {}

    public static void setBacklightColor(int color) {}
}
