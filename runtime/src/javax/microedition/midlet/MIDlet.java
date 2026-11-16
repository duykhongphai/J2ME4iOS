package javax.microedition.midlet;

import javax.microedition.io.ConnectionNotFoundException;

public abstract class MIDlet {
    protected MIDlet() {}

    protected abstract void startApp() throws MIDletStateChangeException;

    protected abstract void pauseApp();

    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;

    public final void notifyDestroyed() { j4i.MidletRunner.destroyed(); }

    public final void notifyPaused() {}

    public final String getAppProperty(String key) {
        if (key == null) throw new NullPointerException();
        return j4i.Sys.getAppProperty(key);
    }

    public final void resumeRequest() {}

    public final boolean platformRequest(String url) throws ConnectionNotFoundException {
        if (url == null || url.length() == 0) return false;
        return j4i.Sys.platformRequest(url);
    }

    public final int checkPermission(String permission) { return 1; }
}
