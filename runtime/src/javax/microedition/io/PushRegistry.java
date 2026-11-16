package javax.microedition.io;

import java.io.IOException;

/** Push activation is not available; registrations are accepted and ignored. */
public class PushRegistry {
    private PushRegistry() {}

    public static void registerConnection(String connection, String midlet, String filter) throws ClassNotFoundException, IOException {
        if (connection == null || midlet == null || filter == null) throw new IllegalArgumentException();
    }

    public static boolean unregisterConnection(String connection) { return false; }

    public static String[] listConnections(boolean available) { return new String[0]; }

    public static String getMIDlet(String connection) { return null; }

    public static String getFilter(String connection) { return null; }

    public static long registerAlarm(String midlet, long time) throws ClassNotFoundException, ConnectionNotFoundException { return 0; }
}
