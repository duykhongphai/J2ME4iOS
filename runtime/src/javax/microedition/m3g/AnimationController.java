package javax.microedition.m3g;

public class AnimationController extends Object3D {
    int activeStart, activeEnd;
    float refSequenceTime;
    int refWorldTime;
    float speed = 1;
    float weight = 1;

    public AnimationController() {}

    Object3D dup() {
        AnimationController c = new AnimationController();
        c.activeStart = activeStart;
        c.activeEnd = activeEnd;
        c.refSequenceTime = refSequenceTime;
        c.refWorldTime = refWorldTime;
        c.speed = speed;
        c.weight = weight;
        return c;
    }

    boolean isActive(int worldTime) {
        if (activeStart == activeEnd) return true;
        return worldTime >= activeStart && worldTime < activeEnd;
    }

    float sequenceTime(int worldTime) { return refSequenceTime + speed * (worldTime - refWorldTime); }

    public void setActiveInterval(int start, int end) {
        if (start > end) throw new IllegalArgumentException();
        activeStart = start;
        activeEnd = end;
    }

    public int getActiveIntervalStart() { return activeStart; }

    public int getActiveIntervalEnd() { return activeEnd; }

    public void setSpeed(float speed, int worldTime) {
        refSequenceTime = sequenceTime(worldTime);
        refWorldTime = worldTime;
        this.speed = speed;
    }

    public float getSpeed() { return speed; }

    public void setPosition(float sequenceTime, int worldTime) {
        refSequenceTime = sequenceTime;
        refWorldTime = worldTime;
    }

    public float getPosition(int worldTime) { return sequenceTime(worldTime); }

    public int getRefWorldTime() { return refWorldTime; }

    public void setWeight(float weight) {
        if (weight < 0) throw new IllegalArgumentException();
        this.weight = weight;
    }

    public float getWeight() { return weight; }
}
