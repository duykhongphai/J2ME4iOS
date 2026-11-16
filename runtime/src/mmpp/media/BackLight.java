package mmpp.media;

/** LG backlight control. The host keeps the screen lit; all no-ops. */
public class BackLight {
    private static int color = 0xFFFFFF;

    private BackLight() {}

    public static int getColor() { return color; }

    public static int numColors() { return 1; }

    public static void off() {}

    public static void on(int timeout) {}

    public static void setColor(int RGB) { color = RGB & 0xFFFFFF; }
}
