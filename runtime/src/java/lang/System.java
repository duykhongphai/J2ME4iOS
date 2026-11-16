package java.lang;

import java.io.PrintStream;

public final class System {
    public static final PrintStream out = new PrintStream(new j4i.ConsoleStream(false));
    public static final PrintStream err = new PrintStream(new j4i.ConsoleStream(true));

    private System() {}

    public static native long currentTimeMillis();

    public static native void arraycopy(Object src, int srcPos, Object dst, int dstPos, int length);

    public static native int identityHashCode(Object x);

    public static String getProperty(String key) {
        if (key == null) throw new NullPointerException();
        return j4i.Sys.getProperty(key);
    }

    public static void exit(int status) { Runtime.getRuntime().exit(status); }

    public static void gc() { Runtime.getRuntime().gc(); }
}
