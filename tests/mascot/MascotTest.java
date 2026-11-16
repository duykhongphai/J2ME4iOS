import com.mascotcapsule.micro3d.v3.*;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/** Visual test for Mascot Capsule v3: animated textured figure + colored primitives + point sprite. */
public class MascotTest extends MIDlet {
    protected void startApp() {
        View v = new View();
        Display.getDisplay(this).setCurrent(v);
        new Thread(v).start();
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean u) {}

    static class View extends Canvas implements Runnable {
        Graphics3D g3 = new Graphics3D();
        Figure fig;
        ActionTable act;
        Texture tex;
        FigureLayout layout = new FigureLayout();
        Effect3D effect = new Effect3D();
        AffineTrans view = new AffineTrans();
        String status = "";
        int frame;

        View() {
            setFullScreenMode(true);
            try {
                fig = new Figure("/cube.mbac");
                act = new ActionTable("/cube.mtra");
                tex = new Texture("/tex.bmp", true);
                fig.setTexture(tex);
                status = "frames " + (act.getNumFrames(0) >> 16) + " patterns " + fig.getNumPattern();
            } catch (Exception e) {
                status = "load: " + e;
                e.printStackTrace();
            }
            view.lookAt(new Vector3D(0, -500, -1500), new Vector3D(0, 500, 1500), new Vector3D(0, 4096, 0));
            layout.setAffineTrans(view);
            layout.setPerspective(100, 10000, 512);
            layout.setCenter(120, 160);
            effect.setLight(new Light(new Vector3D(-2000, -3000, -2000), 3000, 1200));
        }

        public void run() {
            while (true) {
                frame += 1 << 16;
                if (act != null && frame >= act.getNumFrames(0)) frame = 0;
                repaint();
                serviceRepaints();
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                }
            }
        }

        protected void paint(javax.microedition.lcdui.Graphics g) {
            g.setColor(0x203048);
            g.fillRect(0, 0, getWidth(), getHeight());
            g3.bind(g);
            try {
                if (fig != null) {
                    fig.setPosture(act, 0, frame);
                    g3.renderFigure(fig, 0, 0, layout, effect);
                }
                // colored triangles (per face) at the bottom: parallel projection
                FigureLayout pl = new FigureLayout(null, 4096, 4096, 120, 270);
                int[] tri = {-60, -20, 0, 0, 40, 0, 60, -20, 0, -100, 30, 0, -70, 30, 0, -85, -10, 0};
                g3.renderPrimitives(null, 0, 0, pl, new Effect3D(), Graphics3D.PRIMITVE_TRIANGLES | Graphics3D.PDATA_COLOR_PER_FACE, 2, tri,
                                    new int[0], new int[0], new int[] {0xFFD020, 0x30C0FF});
                // point sprite with the model texture
                int[] sp = {80, -10, 0};
                int[] prm = {40, 40, 0, 8, 8, 56, 56, Graphics3D.POINT_SPRITE_PIXEL_SIZE};
                g3.renderPrimitives(tex, 0, 0, pl, new Effect3D(), Graphics3D.PRIMITVE_POINT_SPRITES
                    | Graphics3D.PDATA_POINT_SPRITE_PARAMS_PER_CMD | Graphics3D.PATTR_COLORKEY, 1, sp, new int[0], prm, new int[0]);
                g3.flush();
            } finally {
                g3.release(g);
            }
            g.setColor(0xFFFFFF);
            g.drawString(status + " f" + (frame >> 16), 2, 2, 20);
        }
    }
}
