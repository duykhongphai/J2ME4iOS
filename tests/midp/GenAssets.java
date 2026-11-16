import java.awt.*;
import java.awt.image.*;
import java.io.File;
import javax.imageio.ImageIO;

/** Host-side helper (runs on the desktop JVM) that writes PNG test assets. */
public class GenAssets {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        dir.mkdirs();
        BufferedImage rgba = new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = rgba.createGraphics();
        g.setColor(new Color(255, 200, 0, 255));
        g.fillOval(0, 0, 30, 30);
        g.setColor(new Color(0, 0, 255, 128));
        g.fillRect(10, 5, 30, 20);
        g.setColor(Color.RED);
        g.drawString("A", 2, 12);
        g.dispose();
        ImageIO.write(rgba, "png", new File(dir, "rgba.png"));

        byte[] r = new byte[4], gr = new byte[4], b = new byte[4], a = new byte[4];
        int[][] c = {{0, 0, 0, 0}, {255, 0, 0, 255}, {0, 255, 0, 255}, {255, 255, 255, 255}};
        for (int i = 0; i < 4; i++) {
            r[i] = (byte) c[i][0];
            gr[i] = (byte) c[i][1];
            b[i] = (byte) c[i][2];
            a[i] = (byte) c[i][3];
        }
        IndexColorModel cm = new IndexColorModel(2, 4, r, gr, b, a);
        BufferedImage pal = new BufferedImage(40, 40, BufferedImage.TYPE_BYTE_BINARY, cm);
        for (int y = 0; y < 40; y++)
            for (int x = 0; x < 40; x++) pal.getRaster().setSample(x, y, 0, ((x / 10) + (y / 10)) % 4);
        ImageIO.write(pal, "png", new File(dir, "pal.png"));
    }
}
