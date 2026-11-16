package java.util;

public abstract class TimerTask implements Runnable {
    static final int VIRGIN = 0, SCHEDULED = 1, EXECUTED = 2, CANCELLED = 3;

    int state = VIRGIN;
    long nextTime;
    long lastTime;
    long period;
    boolean fixedRate;
    Timer timer;

    protected TimerTask() {}

    public abstract void run();

    public boolean cancel() {
        boolean r = state == SCHEDULED && (period != 0 || nextTime > System.currentTimeMillis());
        state = CANCELLED;
        if (timer != null) {
            synchronized (timer.queue) {
                timer.queue.removeElement(this);
            }
        }
        return r;
    }

    public long scheduledExecutionTime() { return period < 0 ? nextTime + period : lastTime; }
}
