package javax.microedition.media;

import java.util.Vector;
import javax.microedition.media.control.ToneControl;
import javax.microedition.media.control.VolumeControl;

final class PlayerImpl implements Player, VolumeControl, ToneControl {
    private static final Vector active = new Vector();

    private final byte[] data;
    private final String type;
    private int state = UNREALIZED;
    private int loops = 1;
    private int handle;
    private final Vector listeners = new Vector();
    private int level = 100;
    private boolean muted;
    private long startedAt;
    private long mediaTime;

    PlayerImpl(byte[] data, String type) {
        this.data = data;
        this.type = type;
    }

    private void checkClosed() {
        if (state == CLOSED) throw new IllegalStateException("Player is closed");
    }

    public void realize() throws MediaException {
        checkClosed();
        if (state == UNREALIZED) state = REALIZED;
    }

    public void prefetch() throws MediaException {
        checkClosed();
        realize();
        if (state == REALIZED) {
            if (handle == 0 && data != null) {
                handle = j4i.Sys.audioCreate(data, type);
                if (handle > 0) j4i.Sys.audioVolume(handle, muted ? 0 : level);
            }
            state = PREFETCHED;
        }
    }

    public void start() throws MediaException {
        checkClosed();
        prefetch();
        if (state == STARTED) return;
        if (handle > 0) j4i.Sys.audioPlay(handle, loops);
        state = STARTED;
        startedAt = System.currentTimeMillis();
        synchronized (active) {
            if (!active.contains(this)) active.addElement(this);
        }
        fire(PlayerListener.STARTED, new Long(mediaTime));
    }

    public void stop() throws MediaException {
        checkClosed();
        if (state != STARTED) return;
        if (handle > 0) j4i.Sys.audioStop(handle);
        state = PREFETCHED;
        mediaTime += (System.currentTimeMillis() - startedAt) * 1000L;
        synchronized (active) {
            active.removeElement(this);
        }
        fire(PlayerListener.STOPPED, new Long(mediaTime));
    }

    public void deallocate() {
        checkClosed();
        if (state == STARTED) {
            try {
                stop();
            } catch (MediaException e) {
            }
        }
        if (state >= PREFETCHED) state = REALIZED;
    }

    public void close() {
        if (state == CLOSED) return;
        if (handle > 0) {
            j4i.Sys.audioStop(handle);
            j4i.Sys.audioClose(handle);
            handle = 0;
        }
        state = CLOSED;
        synchronized (active) {
            active.removeElement(this);
        }
        fire(PlayerListener.CLOSED, null);
    }

    public void setTimeBase(TimeBase master) throws MediaException {}

    public TimeBase getTimeBase() { return Manager.getSystemTimeBase(); }

    public long setMediaTime(long now) throws MediaException {
        checkClosed();
        mediaTime = Math.max(0, now);
        return mediaTime;
    }

    public long getMediaTime() {
        if (state == STARTED) return mediaTime + (System.currentTimeMillis() - startedAt) * 1000L;
        return mediaTime;
    }

    public int getState() {
        checkEnd();
        return state;
    }

    public long getDuration() { return TIME_UNKNOWN; }

    public String getContentType() { return type; }

    public void setLoopCount(int count) {
        checkClosed();
        if (count == 0 || state == STARTED) throw new IllegalArgumentException();
        loops = count;
    }

    public void addPlayerListener(PlayerListener l) {
        checkClosed();
        if (l != null && !listeners.contains(l)) listeners.addElement(l);
    }

    public void removePlayerListener(PlayerListener l) { listeners.removeElement(l); }

    public Control[] getControls() { return new Control[] {this}; }

    public Control getControl(String controlType) {
        if (controlType == null) throw new IllegalArgumentException();
        if (controlType.endsWith("VolumeControl")) return this;
        if (controlType.endsWith("ToneControl")) return this;
        return null;
    }

    /* VolumeControl */
    public void setMute(boolean mute) {
        muted = mute;
        if (handle > 0) j4i.Sys.audioVolume(handle, muted ? 0 : level);
    }

    public boolean isMuted() { return muted; }

    public int setLevel(int l) {
        level = Math.max(0, Math.min(100, l));
        if (handle > 0 && !muted) j4i.Sys.audioVolume(handle, level);
        fire(PlayerListener.VOLUME_CHANGED, this);
        return level;
    }

    public int getLevel() { return muted ? -1 : level; }

    /* ToneControl */
    public void setSequence(byte[] sequence) {
        if (sequence == null) throw new IllegalArgumentException();
    }

    private void checkEnd() {
        if (state != STARTED) return;
        boolean ended;
        if (handle > 0) ended = !j4i.Sys.audioIsPlaying(handle);
        else ended = loops != -1 && System.currentTimeMillis() - startedAt > 300;
        if (ended) {
            state = PREFETCHED;
            mediaTime = 0;
            synchronized (active) {
                active.removeElement(this);
            }
            fire(PlayerListener.END_OF_MEDIA, new Long(0));
        }
    }

    static boolean poll() {
        Object[] ps;
        synchronized (active) {
            if (active.isEmpty()) return false;
            ps = new Object[active.size()];
            active.copyInto(ps);
        }
        for (int i = 0; i < ps.length; i++) ((PlayerImpl) ps[i]).checkEnd();
        synchronized (active) {
            return !active.isEmpty();
        }
    }

    private void fire(final String event, final Object data) {
        if (listeners.isEmpty()) return;
        final Object[] ls;
        synchronized (listeners) {
            ls = new Object[listeners.size()];
            listeners.copyInto(ls);
        }
        final Player self = this;
        Runnable r = new Runnable() {
            public void run() {
                for (int i = 0; i < ls.length; i++) {
                    try {
                        ((PlayerListener) ls[i]).playerUpdate(self, event, data);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        };
        javax.microedition.lcdui.Display d = javax.microedition.lcdui.Display.j4iInstance();
        if (d != null) d.callSerially(r);
        else r.run();
    }
}
