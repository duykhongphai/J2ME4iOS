import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.*;
import javax.microedition.midlet.MIDlet;

/** Visual test for M3G. Keys: 1 = immediate mode cube, 2 = loaded animated world, 3 = sprites/fog/blending. */
public class M3GTest extends MIDlet {
    protected void startApp() {
        final View v = new View();
        Display.getDisplay(this).setCurrent(v);
        new Thread(v).start();
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean u) {}

    static class View extends Canvas implements Runnable {
        int mode = 1;
        int frames, fps;
        long fpsStart = System.currentTimeMillis();
        long start = System.currentTimeMillis();
        Graphics3D g3d = Graphics3D.getInstance();
        World world;
        String status = "";
        VertexBuffer cubeVb;
        IndexBuffer cubeIb;
        Appearance cubeApp, blendApp;
        Camera cam = new Camera();
        Light light = new Light(), amb = new Light();
        Sprite3D sprite, sprite2;
        Fog fog = new Fog();
        Background bg = new Background();

        View() {
            setFullScreenMode(true);
            try {
                Object3D[] objs = Loader.load("/cube.m3g");
                for (int i = 0; i < objs.length; i++)
                    if (objs[i] instanceof World) world = (World) objs[i];
                status = "loaded " + objs.length + " root(s)";
                Object3D m = world.find(42);
                status += m instanceof Mesh ? ", mesh ok" : ", NO MESH";
            } catch (Exception e) {
                status = "load failed: " + e;
                e.printStackTrace();
            }
            buildCube();
            cam.setPerspective(60, 240f / 320f, 1, 100);
            light.setMode(Light.DIRECTIONAL);
            light.setColor(0xFFFFFF);
            amb.setMode(Light.AMBIENT);
            amb.setColor(0x404040);
            fog.setMode(Fog.LINEAR);
            fog.setLinear(3, 12);
            fog.setColor(0x8090A0);
            bg.setColor(0xFF8090A0);
            Image2D star = new Image2D(Image2D.RGBA, makeStar());
            Appearance sa = new Appearance();
            CompositingMode cm = new CompositingMode();
            cm.setBlending(CompositingMode.ALPHA);
            cm.setAlphaThreshold(0.1f);
            sa.setCompositingMode(cm);
            sprite = new Sprite3D(true, star, sa);
            sprite2 = new Sprite3D(false, star, sa);
            blendApp = new Appearance();
            CompositingMode bm = new CompositingMode();
            bm.setBlending(CompositingMode.ALPHA);
            blendApp.setCompositingMode(bm);
            PolygonMode pm = new PolygonMode();
            pm.setCulling(PolygonMode.CULL_NONE);
            blendApp.setPolygonMode(pm);
            Material mm = new Material();
            mm.setColor(Material.DIFFUSE, 0x8000C0FF);
            mm.setColor(Material.EMISSIVE, 0x002040);
            blendApp.setMaterial(mm);
        }

        static Image makeStar() {
            Image img = Image.createImage(32, 32);
            int[] px = new int[32 * 32];
            for (int y = 0; y < 32; y++)
                for (int x = 0; x < 32; x++) {
                    float dx = x - 15.5f, dy = y - 15.5f;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    int a = d < 14 ? (int) (255 * (1 - d / 14)) : 0;
                    px[y * 32 + x] = (a << 24) | 0xFFE040;
                }
            return Image.createRGBImage(px, 32, 32, true);
        }

        void buildCube() {
            /* each face: corners (-,-),(+,-),(-,+),(+,+) in the (u, w) plane with u x w = n, which is a CCW strip */
            int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            short[] pos = new short[72];
            byte[] nrm = new byte[72];
            short[] tc = new short[48];
            int v = 0;
            for (int f = 0; f < 6; f++) {
                int[] n = faces[f];
                int[] u = n[0] != 0 ? new int[] {0, 1, 0} : new int[] {1, 0, 0};
                int[] w = {n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0]};
                int[][] corners = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
                for (int k = 0; k < 4; k++) {
                    for (int i = 0; i < 3; i++) {
                        pos[v * 3 + i] = (short) (n[i] + corners[k][0] * u[i] + corners[k][1] * w[i]);
                        nrm[v * 3 + i] = (byte) (n[i] * 127);
                    }
                    tc[v * 2] = (short) (corners[k][0] > 0 ? 1 : 0);
                    tc[v * 2 + 1] = (short) (corners[k][1] > 0 ? 0 : 1);
                    v++;
                }
            }
            byte[] col = new byte[24 * 3];
            for (int i = 0; i < 24; i++) {
                col[i * 3] = (byte) (i * 10);
                col[i * 3 + 1] = (byte) (255 - i * 10);
                col[i * 3 + 2] = (byte) 128;
            }
            VertexArray pa = new VertexArray(24, 3, 2);
            pa.set(0, 24, pos);
            VertexArray na = new VertexArray(24, 3, 1);
            na.set(0, 24, nrm);
            VertexArray ta = new VertexArray(24, 2, 2);
            ta.set(0, 24, tc);
            cubeVb = new VertexBuffer();
            cubeVb.setPositions(pa, 1, null);
            cubeVb.setNormals(na);
            cubeVb.setTexCoords(0, ta, 1, null);
            cubeIb = new TriangleStripArray(0, new int[] {4, 4, 4, 4, 4, 4});
            cubeApp = new Appearance();
            Material m = new Material();
            m.setColor(Material.DIFFUSE, 0xFFFFFFFF);
            m.setColor(Material.SPECULAR, 0xFFFFFF);
            m.setShininess(20);
            cubeApp.setMaterial(m);
            Image tex = Image.createImage(16, 16);
            Graphics g = tex.getGraphics();
            for (int y = 0; y < 4; y++)
                for (int x = 0; x < 4; x++) {
                    g.setColor(((x + y) & 1) == 0 ? 0x2060E0 : 0xF0F0F0);
                    g.fillRect(x * 4, y * 4, 4, 4);
                }
            Texture2D t = new Texture2D(new Image2D(Image2D.RGB, tex));
            t.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_LINEAR);
            cubeApp.setTexture(0, t);
        }

        protected void keyPressed(int k) {
            if (k >= '1' && k <= '3') mode = k - '0';
            repaint();
        }

        public void run() {
            while (true) {
                repaint();
                serviceRepaints();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }
            }
        }

        protected void paint(Graphics g) {
            int t = (int) (System.currentTimeMillis() - start);
            g3d.bindTarget(g);
            try {
                if (mode == 1) {
                    Background b = new Background();
                    b.setColor(0xFF102030);
                    g3d.clear(b);
                    Transform ct = new Transform();
                    ct.postTranslate(0, 0, 5);
                    g3d.setCamera(cam, ct);
                    g3d.resetLights();
                    Transform lt = new Transform();
                    lt.postRotate(-30, 1, 1, 0);
                    g3d.addLight(light, lt);
                    g3d.addLight(amb, null);
                    Transform mt = new Transform();
                    mt.postRotate(30 + t * 0.05f, 0, 1, 0);
                    mt.postRotate(25, 1, 0, 0);
                    g3d.render(cubeVb, cubeIb, cubeApp, mt);
                    Transform qt = new Transform();
                    qt.postTranslate(0.8f, -0.6f, 1.6f);
                    qt.postRotate(t * 0.03f, 0, 0, 1);
                    g3d.render(cubeVb, cubeIb, blendApp, qt);
                } else if (mode == 2 && world != null) {
                    world.animate(t);
                    g3d.render(world);
                } else if (mode == 3) {
                    g3d.clear(bg);
                    Transform ct = new Transform();
                    ct.postTranslate(0, 1, 6);
                    g3d.setCamera(cam, ct);
                    g3d.resetLights();
                    g3d.addLight(amb, null);
                    Transform lt = new Transform();
                    lt.postRotate(-40, 1, 0, 0);
                    g3d.addLight(light, lt);
                    cubeApp.setFog(fog);
                    for (int i = 0; i < 5; i++) {
                        Transform mt = new Transform();
                        mt.postTranslate(-2 + i, 0, -i * 2.5f + 2);
                        mt.postRotate(t * 0.04f + i * 20, 0, 1, 0);
                        g3d.render(cubeVb, cubeIb, cubeApp, mt);
                    }
                    cubeApp.setFog(null);
                    Transform st = new Transform();
                    st.postTranslate(0, 2.2f, 1);
                    g3d.render(sprite, st);
                    Transform st2 = new Transform();
                    st2.postTranslate(1.5f, 2.2f, 1);
                    g3d.render(sprite2, st2);
                }
            } finally {
                g3d.releaseTarget();
            }
            frames++;
            long now = System.currentTimeMillis();
            if (now - fpsStart >= 1000) {
                fps = frames * 1000 / (int) (now - fpsStart);
                frames = 0;
                fpsStart = now;
            }
            g.setColor(0xFFFFFF);
            g.drawString("mode " + mode + " fps " + fps + " " + status, 2, 2, Graphics.TOP | Graphics.LEFT);
        }
    }
}
