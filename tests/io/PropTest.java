/** Checks JAD attributes and system property overrides. Run with --jad test.jad --prop ... (J2ME4iOS only). */
public class PropTest extends javax.microedition.midlet.MIDlet {
    static int failures;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failures++;
    }

    protected void startApp() {
        check("JAD attribute", "from-jad".equals(getAppProperty("Game-Server")));
        check("JAD overrides manifest", "2.0".equals(getAppProperty("MIDlet-Version")));
        check("manifest attribute", "PropTest".equals(getAppProperty("MIDlet-Name")));
        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILURES");
        notifyDestroyed();
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) {}
}
