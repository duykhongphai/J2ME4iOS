package com.motorola.funlight;

/**
 * Motorola FunLight (colored phone LEDs). There are no such lights here; regions remember
 * their color so games reading it back see consistent values.
 */
public class FunLight {
    public static final int BLACK = 0x00000000;
    public static final int BLANK = 0;
    public static final int BLUE = 0x000000FF;
    public static final int CYAN = 0x0000FFFF;
    public static final int GREEN = 0x0000FF00;
    public static final int IGNORED = 2;
    public static final int MAGENTA = 0x00FF00FF;
    public static final int OFF = 0x00000000;
    public static final int ON = 0x00FFFFFF;
    public static final int QUEUED = 1;
    public static final int RED = 0x00FF0000;
    public static final int SUCCESS = 0;
    public static final int WHITE = 0x00FFFFFF;
    public static final int YELLOW = 0x00FFFF00;

    private static final int REGION_COUNT = 4;
    private static final Region[] REGIONS = new Region[REGION_COUNT];

    static {
        for (int i = 0; i < REGION_COUNT; i++) REGIONS[i] = new LightRegion(i + 1);
    }

    private FunLight() {}

    private static final class LightRegion implements Region {
        private final int id;
        private int color = OFF;

        LightRegion(int id) { this.id = id; }

        public int getColor() { return color; }

        public int getControl() { return SUCCESS; }

        public int getID() { return id; }

        public void releaseControl() {}

        public int setColor(byte red, byte green, byte blue) {
            return setColor(((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF));
        }

        public int setColor(int c) {
            color = c & 0xFFFFFF;
            return SUCCESS;
        }

        public String toString() { return "FunLight region " + id; }
    }

    public static int getControl() { return SUCCESS; }

    public static void releaseControl() {}

    /** Region IDs are 1..4; other IDs yield null. */
    public static Region getRegion(int ID) {
        if (ID < 1 || ID > REGION_COUNT) return null;
        return REGIONS[ID - 1];
    }

    public static Region[] getRegions() {
        Region[] r = new Region[REGION_COUNT];
        System.arraycopy(REGIONS, 0, r, 0, REGION_COUNT);
        return r;
    }

    public static int[] getRegionsIDs() {
        int[] ids = new int[REGION_COUNT];
        for (int i = 0; i < REGION_COUNT; i++) ids[i] = i + 1;
        return ids;
    }

    /** Sets all regions. */
    public static int setColor(byte red, byte green, byte blue) {
        for (int i = 0; i < REGION_COUNT; i++) REGIONS[i].setColor(red, green, blue);
        return SUCCESS;
    }

    public static int setColor(int color) {
        for (int i = 0; i < REGION_COUNT; i++) REGIONS[i].setColor(color);
        return SUCCESS;
    }
}
