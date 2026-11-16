package java.lang;

public class Throwable {
    private String detailMessage;
    private Object trace;
    private Throwable cause;

    public Throwable() { fillInStackTrace0(); }

    public Throwable(String message) {
        detailMessage = message;
        fillInStackTrace0();
    }

    public Throwable(String message, Throwable cause) {
        detailMessage = message;
        this.cause = cause;
        fillInStackTrace0();
    }

    public Throwable(Throwable cause) {
        this.cause = cause;
        if (cause != null) detailMessage = cause.toString();
        fillInStackTrace0();
    }

    public String getMessage() { return detailMessage; }

    public String getLocalizedMessage() { return getMessage(); }

    public Throwable getCause() { return cause; }

    public Throwable initCause(Throwable c) {
        cause = c;
        return this;
    }

    public String toString() {
        String n = getClass().getName();
        String m = getLocalizedMessage();
        return m == null ? n : n + ": " + m;
    }

    public void printStackTrace() {
        printStackTrace0();
        if (cause != null && cause != this) System.err.println("Caused by: " + cause);
    }

    public Throwable fillInStackTrace() { return this; }

    private native void fillInStackTrace0();

    private native void printStackTrace0();
}
