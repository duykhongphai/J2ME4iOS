package j4i;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletAccess;

/** MIDlet lifecycle and the event thread. */
public final class MidletRunner {
    private static MIDlet midlet;
    private static boolean paused;
    private static int reported;

    private MidletRunner() {}

    static void run(String cls) throws Throwable {
        Display.j4iInit();
        Thread ev = new Thread(new Runnable() {
            public void run() { eventLoop(); }
        }, "EventThread");
        ev.start();
        Object o = Class.forName(cls).newInstance();
        if (!(o instanceof MIDlet)) throw new ClassCastException(cls + " is not a MIDlet");
        midlet = (MIDlet) o;
        MIDletAccess.startApp(midlet);
    }

    static void eventLoop() {
        int[] ev = new int[3];
        Display d = Display.j4iInstance();
        while (true) {
            try {
                d.j4iProcessPending();
                if (Sys.waitEvent(ev, d.j4iTimeout())) d.j4iEvent(ev[0], ev[1], ev[2]);
            } catch (Throwable t) {
                report("event loop", t);
            }
        }
    }

    public static void report(String where, Throwable t) {
        if (reported++ < 50) {
            System.err.println("Exception in " + where + ": " + t);
            t.printStackTrace();
        }
    }

    public static void destroyed() { Sys.exit(0); }

    public static void pause() {
        if (midlet == null || paused) return;
        paused = true;
        try {
            MIDletAccess.pauseApp(midlet);
        } catch (Throwable t) {
            report("pauseApp", t);
        }
    }

    public static void resume() {
        if (midlet == null || !paused) return;
        paused = false;
        try {
            MIDletAccess.startApp(midlet);
        } catch (Throwable t) {
            report("startApp", t);
        }
    }

    public static void exitRequested() {
        if (midlet != null) {
            try {
                MIDletAccess.destroyApp(midlet, true);
            } catch (Throwable t) {
                report("destroyApp", t);
            }
        }
        Sys.exit(0);
    }

    static void showError(Throwable t) {
        Display d = Display.j4iInstance();
        if (d == null) {
            Sys.exit(1);
            return;
        }
        Form f = new Form("Error");
        f.append("The game stopped with an error:\n\n" + t.toString());
        f.addCommand(new Command("Exit", Command.EXIT, 1));
        f.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable disp) { Sys.exit(1); }
        });
        d.setCurrent(f);
    }
}
