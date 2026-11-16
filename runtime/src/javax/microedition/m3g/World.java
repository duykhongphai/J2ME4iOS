package javax.microedition.m3g;

public class World extends Group {
    Camera activeCamera;
    Background background;

    public World() {}

    Object3D dup() {
        World w = new World();
        copyGroupTo(w);
        w.background = background;
        /* keep the active camera pointing into the duplicated tree when possible */
        w.activeCamera = activeCamera;
        if (activeCamera != null) {
            Object3D c = w.find(activeCamera.getUserID());
            if (c instanceof Camera && activeCamera.getUserID() != 0) w.activeCamera = (Camera) c;
        }
        return w;
    }

    int refs(Object3D[] out, int pos) {
        pos = super.refs(out, pos);
        if (activeCamera != null && activeCamera.parent == null) pos = add(out, pos, activeCamera);
        return add(out, pos, background);
    }

    public void setActiveCamera(Camera camera) {
        if (camera == null) throw new NullPointerException();
        activeCamera = camera;
    }

    public Camera getActiveCamera() { return activeCamera; }

    public void setBackground(Background background) { this.background = background; }

    public Background getBackground() { return background; }
}
