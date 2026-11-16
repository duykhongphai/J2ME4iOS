package javax.microedition.midlet;

/** Lets the J2ME4iOS launcher call the protected lifecycle methods. Not part of MIDP. */
public final class MIDletAccess {
    private MIDletAccess() {}

    public static void startApp(MIDlet m) throws MIDletStateChangeException { m.startApp(); }

    public static void pauseApp(MIDlet m) { m.pauseApp(); }

    public static void destroyApp(MIDlet m, boolean unconditional) throws MIDletStateChangeException { m.destroyApp(unconditional); }
}
