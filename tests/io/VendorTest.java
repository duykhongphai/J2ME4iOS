import javax.microedition.lcdui.Image;

/** Smoke test for vendor APIs (runs only on J2ME4iOS). */
public class VendorTest {
    static int failures;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        com.siemens.mp.io.File f = new com.siemens.mp.io.File();
        int fd = f.open("vendor.dat");
        f.write(fd, new byte[] {1, 2, 3, 4}, 0, 4);
        f.close(fd);
        fd = f.open("vendor.dat");
        byte[] b = new byte[4];
        int n = f.read(fd, b, 0, 4);
        check("siemens File write/read " + n, n == 4 && b[3] == 4 && f.length(fd) == 4);
        f.close(fd);
        check("siemens File exists", com.siemens.mp.io.File.exists("vendor.dat") == 1);
        check("siemens File delete", com.siemens.mp.io.File.delete("vendor.dat") == 1 && com.siemens.mp.io.File.exists("vendor.dat") == -1);

        com.siemens.mp.game.MelodyComposer mc = new com.siemens.mp.game.MelodyComposer();
        mc.setBPM(120);
        mc.appendNote(com.siemens.mp.game.MelodyComposer.TONE_C1, com.siemens.mp.game.MelodyComposer.TONELENGTH_1_4);
        mc.appendNote(com.siemens.mp.game.MelodyComposer.TONE_E1, com.siemens.mp.game.MelodyComposer.TONELENGTH_1_4);
        com.siemens.mp.game.Melody m = mc.getMelody();
        m.play();
        com.siemens.mp.game.Melody.stop();
        check("siemens melody", mc.length() == 2);
        com.siemens.mp.game.Sound.playTone(440, 100);

        com.siemens.mp.game.ExtendedImage ei = new com.siemens.mp.game.ExtendedImage(Image.createImage(16, 8));
        ei.clear((byte) 0);
        ei.setPixel(3, 2, (byte) 1);
        check("siemens ExtendedImage pixel", ei.getPixel(3, 2) == 1 && ei.getPixel(4, 2) == 0);

        com.samsung.util.Vibration.start(1, 50);
        com.samsung.util.LCDLight.on(100);
        check("samsung SMS unsupported", !com.samsung.util.SMS.isSupported());

        com.motorola.funlight.FunLight.setColor(0xFF0000);
        check("motorola funlight", com.motorola.funlight.FunLight.getRegion(1) != null);

        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILURES");
    }
}
