package j4i;

/** Entry point executed by the VM's main thread. */
public final class Launcher {
    private Launcher() {}

    /**
     * args[0] = "main" | "midlet", args[1] = class name, rest = program arguments.
     */
    public static void main(String[] args) {
        String mode = args[0];
        String cls = args[1];
        try {
            if (mode.equals("main")) {
                String[] rest = new String[args.length - 2];
                System.arraycopy(args, 2, rest, 0, rest.length);
                Sys.invokeMain(Class.forName(cls), rest);
                return;
            }
            MidletRunner.run(cls);
        } catch (Throwable t) {
            System.err.println("Launcher: " + t);
            t.printStackTrace();
            fatal(t);
        }
    }

    static void fatal(Throwable t) {
        try {
            MidletRunner.showError(t);
        } catch (Throwable e) {
            Sys.exit(1);
        }
    }
}
