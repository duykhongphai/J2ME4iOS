package javax.microedition.m3g;

import java.util.Hashtable;
import java.util.Vector;

public abstract class Object3D {
    int userID;
    Object userObject;
    Vector tracks = new Vector();

    Object3D() {}

    public final int animate(int time) {
        animateTree(time, new Hashtable());
        return 0x7FFFFFFF;
    }

    private void animateTree(int time, Hashtable seen) {
        if (seen.containsKey(this)) return;
        seen.put(this, this);
        applyTracks(time);
        int n = refs(null, 0);
        if (n == 0) return;
        Object3D[] r = new Object3D[n];
        refs(r, 0);
        for (int i = 0; i < n; i++)
            if (r[i] != null) r[i].animateTree(time, seen);
    }

    private void applyTracks(int time) {
        int n = tracks.size();
        if (n == 0) return;
        boolean[] done = new boolean[n];
        float[] tmp = new float[16];
        for (int i = 0; i < n; i++) {
            if (done[i]) continue;
            AnimationTrack ti = (AnimationTrack) tracks.elementAt(i);
            int prop = ti.property;
            int comps = ti.sequence.componentCount;
            float[] acc = new float[Math.max(comps, 4)];
            boolean any = false;
            float wsum = 0;
            for (int j = i; j < n; j++) {
                AnimationTrack tj = (AnimationTrack) tracks.elementAt(j);
                if (tj.property != prop) continue;
                done[j] = true;
                AnimationController c = tj.controller;
                if (c == null || !c.isActive(time) || c.weight == 0) continue;
                KeyframeSequence ks = tj.sequence;
                if (ks.keyframeCount == 0) continue;
                ks.sample(c.sequenceTime(time), tmp);
                int cc = Math.min(ks.componentCount, acc.length);
                if (prop == AnimationTrack.ORIENTATION && any) {
                    float dot = acc[0] * tmp[0] + acc[1] * tmp[1] + acc[2] * tmp[2] + acc[3] * tmp[3];
                    if (dot < 0)
                        for (int k = 0; k < 4; k++) tmp[k] = -tmp[k];
                }
                for (int k = 0; k < cc; k++) acc[k] += c.weight * tmp[k];
                wsum += c.weight;
                any = true;
            }
            if (any) {
                if (prop == AnimationTrack.ORIENTATION) Quat.normalize(acc);
                applyAnimation(prop, acc, comps);
            }
        }
    }

    /** Applies an animated property value; subclasses handle their properties and call super. */
    void applyAnimation(int property, float[] v, int comps) {}

    static int colorFrom(float[] v, int comps, int oldArgb) {
        int r = clamp255(v[0]), g = clamp255(v[1]), b = clamp255(v[2]);
        int a = comps >= 4 ? clamp255(v[3]) : (oldArgb >>> 24);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static int clamp255(float f) {
        int i = (int) (f * 255 + 0.5f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    public final Object3D duplicate() {
        Object3D o = dup();
        o.userID = userID;
        o.userObject = userObject;
        o.tracks = new Vector();
        for (int i = 0; i < tracks.size(); i++) o.tracks.addElement(tracks.elementAt(i));
        return o;
    }

    /** Creates a copy of this object's own state (not userID/tracks). */
    abstract Object3D dup();

    public Object3D find(int userID) {
        return findIn(userID, new Hashtable());
    }

    private Object3D findIn(int id, Hashtable seen) {
        if (this.userID == id) return this;
        if (seen.containsKey(this)) return null;
        seen.put(this, this);
        int n = refs(null, 0);
        if (n == 0) return null;
        Object3D[] r = new Object3D[n];
        refs(r, 0);
        for (int i = 0; i < n; i++) {
            if (r[i] == null) continue;
            Object3D f = r[i].findIn(id, seen);
            if (f != null) return f;
        }
        return null;
    }

    public int getReferences(Object3D[] references) {
        int n = refs(null, 0);
        if (references != null) {
            if (references.length < n) throw new IllegalArgumentException();
            refs(references, 0);
        }
        return n;
    }

    /** Stores direct references at out[pos..] (if out != null); returns the new count. */
    int refs(Object3D[] out, int pos) {
        for (int i = 0; i < tracks.size(); i++) pos = add(out, pos, (Object3D) tracks.elementAt(i));
        return pos;
    }

    static int add(Object3D[] out, int pos, Object3D o) {
        if (o == null) return pos;
        if (out != null) out[pos] = o;
        return pos + 1;
    }

    public int getUserID() { return userID; }

    public void setUserID(int userID) { this.userID = userID; }

    public Object getUserObject() { return userObject; }

    public void setUserObject(Object userObject) { this.userObject = userObject; }

    public void addAnimationTrack(AnimationTrack animationTrack) {
        if (animationTrack == null) throw new NullPointerException();
        if (tracks.contains(animationTrack)) throw new IllegalArgumentException("Track already added");
        if (!acceptsProperty(animationTrack.property)) throw new IllegalArgumentException("Property not animatable here");
        tracks.addElement(animationTrack);
    }

    boolean acceptsProperty(int property) { return true; }

    public AnimationTrack getAnimationTrack(int index) { return (AnimationTrack) tracks.elementAt(index); }

    public void removeAnimationTrack(AnimationTrack animationTrack) { tracks.removeElement(animationTrack); }

    public int getAnimationTrackCount() { return tracks.size(); }
}
