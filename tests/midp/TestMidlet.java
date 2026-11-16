import java.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;

/** Visual smoke test for the MIDP implementation. */
public class TestMidlet extends MIDlet implements CommandListener {
    private Display display;
    private TestCanvas canvas;
    private final Command cmdForm = new Command("Form", Command.SCREEN, 1);
    private final Command cmdList = new Command("List", Command.SCREEN, 2);
    private final Command cmdAlert = new Command("Alert", Command.SCREEN, 3);
    private final Command cmdExit = new Command("Exit", Command.EXIT, 1);
    private final Command cmdBack = new Command("Back", Command.BACK, 1);
    int runs;

    public TestMidlet() {}

    protected void startApp() throws MIDletStateChangeException {
        if (display != null) return;
        display = Display.getDisplay(this);
        runs = bumpRuns();
        System.out.println("TestMidlet start, runs=" + runs + " platform=" + System.getProperty("microedition.platform"));
        canvas = new TestCanvas(this);
        canvas.addCommand(cmdForm);
        canvas.addCommand(cmdList);
        canvas.addCommand(cmdAlert);
        canvas.addCommand(cmdExit);
        canvas.setCommandListener(this);
        display.setCurrent(canvas);
        new Thread(canvas).start();
    }

    int bumpRuns() {
        try {
            RecordStore rs = RecordStore.openRecordStore("test", true);
            int n = 0;
            if (rs.getNumRecords() == 0) {
                rs.addRecord(new byte[4], 0, 4);
            } else {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(rs.getRecord(1)));
                n = in.readInt();
            }
            n++;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            new DataOutputStream(bo).writeInt(n);
            byte[] b = bo.toByteArray();
            rs.setRecord(1, b, 0, b.length);
            rs.closeRecordStore();
            return n;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) { canvas.running = false; }

    public void commandAction(Command c, Displayable d) {
        System.out.println("command " + c.getLabel());
        if (c == cmdExit) {
            destroyApp(true);
            notifyDestroyed();
        } else if (c == cmdBack) {
            display.setCurrent(canvas);
        } else if (c == cmdForm) {
            Form f = new Form("Form test");
            f.append(new StringItem("Label:", "Hello from a Form. This text is long enough to wrap onto several lines."));
            f.append(new TextField("Name", "J2ME", 20, TextField.ANY));
            f.append(new ChoiceGroup("Choose", Choice.EXCLUSIVE, new String[] {"One", "Two", "Three"}, null));
            f.append(new Gauge("Volume", true, 10, 4));
            f.addCommand(cmdBack);
            f.setCommandListener(this);
            display.setCurrent(f);
        } else if (c == cmdList) {
            List l = new List("List test", Choice.IMPLICIT, new String[] {"New game", "Continue", "Options", "High scores", "Help"}, null);
            l.addCommand(cmdBack);
            l.setCommandListener(this);
            display.setCurrent(l);
        } else if (c == cmdAlert) {
            Alert a = new Alert("Alert", "This is an alert that times out.", null, AlertType.INFO);
            a.setTimeout(1500);
            display.setCurrent(a, canvas);
        } else if (c == List.SELECT_COMMAND) {
            List l = (List) d;
            System.out.println("selected " + l.getString(l.getSelectedIndex()));
        }
    }
}

class TestCanvas extends GameCanvas implements Runnable {
    final TestMidlet midlet;
    volatile boolean running = true;
    Image png, pal;
    Sprite sprite;
    TiledLayer tiles;
    LayerManager lm = new LayerManager();
    int frame, lastKey, px = 40, py = 200;
    String lastPointer = "-";
    long t0 = System.currentTimeMillis();

    TestCanvas(TestMidlet m) {
        super(false);
        midlet = m;
        setFullScreenMode(false);
        try {
            png = Image.createImage("/rgba.png");
            pal = Image.createImage("/pal.png");
        } catch (IOException e) {
            e.printStackTrace();
        }
        int[] rgb = new int[16 * 16 * 4];
        for (int f = 0; f < 4; f++)
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    boolean on = (x - 8) * (x - 8) + (y - 8) * (y - 8) < 40;
                    rgb[y * 64 + f * 16 + x] = on ? (0xFF000000 | (0x40 * f << 16) | 0x00C000 | (x * 16)) : 0;
                }
        Image sheet = Image.createRGBImage(rgb, 64, 16, true);
        sprite = new Sprite(sheet, 16, 16);
        sprite.defineReferencePixel(8, 8);
        Image tileImg = Image.createImage(32, 16);
        Graphics tg = tileImg.getGraphics();
        tg.setColor(0x806040);
        tg.fillRect(0, 0, 16, 16);
        tg.setColor(0x40A040);
        tg.fillRect(16, 0, 16, 16);
        tg.setColor(0x000000);
        tg.drawRect(0, 0, 15, 15);
        tg.drawRect(16, 0, 15, 15);
        tiles = new TiledLayer(15, 2, tileImg, 16, 16);
        for (int c = 0; c < 15; c++) {
            tiles.setCell(c, 0, 2);
            tiles.setCell(c, 1, 1);
        }
        tiles.setPosition(0, 240);
        lm.append(sprite);
        lm.append(tiles);
    }

    public void run() {
        while (running) {
            int ks = getKeyStates();
            if ((ks & LEFT_PRESSED) != 0) px -= 2;
            if ((ks & RIGHT_PRESSED) != 0) px += 2;
            if ((ks & UP_PRESSED) != 0) py -= 2;
            if ((ks & DOWN_PRESSED) != 0) py += 2;
            draw(getGraphics());
            flushGraphics();
            frame++;
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
            }
        }
    }

    void draw(Graphics g) {
        int w = getWidth(), h = getHeight();
        for (int y = 0; y < h; y += 4) {
            g.setColor(0x10, 0x20 + y * 0x60 / h, 0x60 + y * 0x80 / h);
            g.fillRect(0, y, w, 4);
        }
        g.setColor(0xFFFFFF);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
        g.drawString("J2ME4iOS", w / 2, 4, Graphics.HCENTER | Graphics.TOP);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
        g.drawString("frame " + frame + "  runs " + midlet.runs + "  key " + lastKey, 4, 26, Graphics.LEFT | Graphics.TOP);
        g.drawString("ptr " + lastPointer + "  Tiếng Việt ✓", 4, 40, Graphics.LEFT | Graphics.TOP);

        g.setColor(0xFF4040);
        g.fillRect(8, 60, 30, 20);
        g.setColor(0xFFFF00);
        g.drawRect(44, 60, 30, 20);
        g.setColor(0x40FF40);
        g.fillRoundRect(80, 60, 34, 20, 10, 10);
        g.setColor(0x40FFFF);
        g.fillArc(120, 56, 28, 28, 30, 300);
        g.setColor(0xFF80FF);
        g.drawArc(154, 56, 28, 28, 0, 360);
        g.setColor(0xFFFFFF);
        g.fillTriangle(196, 82, 214, 56, 232, 82);
        g.setStrokeStyle(Graphics.DOTTED);
        g.drawLine(8, 88, 232, 88);
        g.setStrokeStyle(Graphics.SOLID);

        if (png != null) g.drawImage(png, 8, 96, Graphics.TOP | Graphics.LEFT);
        if (pal != null) g.drawImage(pal, 56, 96, Graphics.TOP | Graphics.LEFT);
        if (png != null)
            for (int t = 0; t < 8; t++) g.drawRegion(png, 0, 0, 32, 20, t, 104 + (t % 4) * 34, 96 + (t / 4) * 34, Graphics.TOP | Graphics.LEFT);

        sprite.setFrame((frame / 6) % 4);
        sprite.setRefPixelPosition(px, py);
        tiles.setPosition(-(frame % 16), 240);
        lm.paint(g, 0, 0);
        g.setColor(sprite.collidesWith(tiles, true) ? 0xFF0000 : 0x00FF00);
        g.drawString(sprite.collidesWith(tiles, true) ? "HIT" : "free", w - 4, 170, Graphics.RIGHT | Graphics.TOP);
        long fps = frame * 1000L / Math.max(1, System.currentTimeMillis() - t0);
        g.setColor(0xFFFFFF);
        g.drawString("fps " + fps, w - 4, 26, Graphics.RIGHT | Graphics.TOP);
    }

    protected void keyPressed(int keyCode) { lastKey = keyCode; }

    protected void pointerPressed(int x, int y) { lastPointer = x + "," + y; }
}
