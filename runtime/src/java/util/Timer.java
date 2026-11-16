package java.util;

public class Timer {
    final Vector queue = new Vector();
    boolean cancelled;
    private final Thread thread;

    public Timer() {
        thread = new Thread(new Runnable() {
            public void run() { loop(); }
        }, "Timer");
        thread.start();
    }

    void loop() {
        while (true) {
            TimerTask task;
            long delay;
            synchronized (queue) {
                if (cancelled) return;
                if (queue.isEmpty()) {
                    try {
                        queue.wait();
                    } catch (InterruptedException e) {
                    }
                    continue;
                }
                task = (TimerTask) queue.elementAt(0);
                for (int i = 1; i < queue.size(); i++) {
                    TimerTask t = (TimerTask) queue.elementAt(i);
                    if (t.nextTime < task.nextTime) task = t;
                }
                if (task.state == TimerTask.CANCELLED) {
                    queue.removeElement(task);
                    continue;
                }
                delay = task.nextTime - System.currentTimeMillis();
                if (delay > 0) {
                    try {
                        queue.wait(delay);
                    } catch (InterruptedException e) {
                    }
                    continue;
                }
                if (task.period == 0) {
                    queue.removeElement(task);
                    task.state = TimerTask.EXECUTED;
                } else {
                    task.nextTime = task.fixedRate ? task.nextTime + task.period : System.currentTimeMillis() + task.period;
                }
                task.lastTime = System.currentTimeMillis();
            }
            try {
                task.run();
            } catch (Throwable t) {
                t.printStackTrace();
                synchronized (queue) {
                    cancelled = true;
                    queue.removeAllElements();
                }
                return;
            }
        }
    }

    private void sched(TimerTask task, long time, long period, boolean fixed) {
        if (time < 0) throw new IllegalArgumentException("Illegal execution time.");
        synchronized (queue) {
            if (cancelled) throw new IllegalStateException("Timer already cancelled.");
            if (task.state != TimerTask.VIRGIN) throw new IllegalStateException("Task already scheduled or cancelled");
            task.nextTime = time;
            task.period = period;
            task.fixedRate = fixed;
            task.state = TimerTask.SCHEDULED;
            task.timer = this;
            queue.addElement(task);
            queue.notify();
        }
    }

    public void schedule(TimerTask task, long delay) {
        if (delay < 0) throw new IllegalArgumentException("Negative delay.");
        sched(task, System.currentTimeMillis() + delay, 0, false);
    }

    public void schedule(TimerTask task, Date time) { sched(task, time.getTime(), 0, false); }

    public void schedule(TimerTask task, long delay, long period) {
        if (delay < 0) throw new IllegalArgumentException("Negative delay.");
        if (period <= 0) throw new IllegalArgumentException("Non-positive period.");
        sched(task, System.currentTimeMillis() + delay, period, false);
    }

    public void schedule(TimerTask task, Date firstTime, long period) {
        if (period <= 0) throw new IllegalArgumentException("Non-positive period.");
        sched(task, firstTime.getTime(), period, false);
    }

    public void scheduleAtFixedRate(TimerTask task, long delay, long period) {
        if (delay < 0) throw new IllegalArgumentException("Negative delay.");
        if (period <= 0) throw new IllegalArgumentException("Non-positive period.");
        sched(task, System.currentTimeMillis() + delay, period, true);
    }

    public void scheduleAtFixedRate(TimerTask task, Date firstTime, long period) {
        if (period <= 0) throw new IllegalArgumentException("Non-positive period.");
        sched(task, firstTime.getTime(), period, true);
    }

    public void cancel() {
        synchronized (queue) {
            cancelled = true;
            queue.removeAllElements();
            queue.notify();
        }
    }
}
