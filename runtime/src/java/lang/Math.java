package java.lang;

public final class Math {
    public static final double E = 2.718281828459045;
    public static final double PI = 3.141592653589793;

    private static java.util.Random rnd;

    private Math() {}

    public static int abs(int a) { return a < 0 ? -a : a; }

    public static long abs(long a) { return a < 0 ? -a : a; }

    public static float abs(float a) { return a <= 0.0f ? 0.0f - a : a; }

    public static double abs(double a) { return a <= 0.0 ? 0.0 - a : a; }

    public static int max(int a, int b) { return a >= b ? a : b; }

    public static long max(long a, long b) { return a >= b ? a : b; }

    public static float max(float a, float b) {
        if (a != a) return a;
        return a >= b ? a : b;
    }

    public static double max(double a, double b) {
        if (a != a) return a;
        return a >= b ? a : b;
    }

    public static int min(int a, int b) { return a <= b ? a : b; }

    public static long min(long a, long b) { return a <= b ? a : b; }

    public static float min(float a, float b) {
        if (a != a) return a;
        return a <= b ? a : b;
    }

    public static double min(double a, double b) {
        if (a != a) return a;
        return a <= b ? a : b;
    }

    public static native double sin(double a);

    public static native double cos(double a);

    public static native double tan(double a);

    public static native double asin(double a);

    public static native double acos(double a);

    public static native double atan(double a);

    public static native double atan2(double y, double x);

    public static native double sqrt(double a);

    public static native double ceil(double a);

    public static native double floor(double a);

    public static native double exp(double a);

    public static native double log(double a);

    public static native double pow(double a, double b);

    public static double rint(double a) {
        double f = floor(a);
        double d = a - f;
        if (d > 0.5) return f + 1.0;
        if (d < 0.5) return f;
        return (((long) f) & 1) == 0 ? f : f + 1.0;
    }

    public static int round(float a) { return (int) floor(a + 0.5f); }

    public static long round(double a) { return (long) floor(a + 0.5); }

    public static double toRadians(double angdeg) { return angdeg / 180.0 * PI; }

    public static double toDegrees(double angrad) { return angrad * 180.0 / PI; }

    public static synchronized double random() {
        if (rnd == null) rnd = new java.util.Random();
        return rnd.nextDouble();
    }
}
