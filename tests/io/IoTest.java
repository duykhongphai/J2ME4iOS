import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.ToneControl;

/** Self-checking test for JSR-75 files, tone synthesis, inflate and (optionally) HTTP. Runs only on J2ME4iOS. */
public class IoTest {
    static int failures;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        // ---- file system
        Enumeration roots = FileSystemRegistry.listRoots();
        boolean hasRoot1 = false;
        while (roots.hasMoreElements()) if ("root1/".equals(roots.nextElement())) hasRoot1 = true;
        check("listRoots has root1/", hasRoot1);

        FileConnection dir = (FileConnection) Connector.open("file:///root1/iotest/");
        if (dir.exists()) {
            Enumeration e = dir.list("*", true);
            while (e.hasMoreElements()) {
                FileConnection f = (FileConnection) Connector.open("file:///root1/iotest/" + e.nextElement());
                f.delete();
                f.close();
            }
            dir.delete();
        }
        check("dir gone", !dir.exists());
        dir.mkdir();
        check("mkdir", dir.exists() && dir.isDirectory());

        FileConnection fc = (FileConnection) Connector.open("file:///root1/iotest/a%20b.txt");
        check("new file does not exist", !fc.exists());
        fc.create();
        check("create", fc.exists() && !fc.isDirectory() && fc.fileSize() == 0);
        DataOutputStream out = fc.openDataOutputStream();
        out.writeUTF("Xin chào");
        out.writeInt(12345);
        out.close();
        check("fileSize after write", fc.fileSize() == 2 + "Xin chào".getBytes("UTF-8").length + 4);
        DataInputStream in = fc.openDataInputStream();
        check("read back", in.readUTF().equals("Xin chào") && in.readInt() == 12345);
        in.close();
        OutputStream app = fc.openOutputStream(fc.fileSize());
        app.write(new byte[] {1, 2, 3});
        app.close();
        check("append", fc.fileSize() == 2 + "Xin chào".getBytes("UTF-8").length + 7);
        OutputStream over = fc.openOutputStream(0);
        over.write(0);
        over.write(5);
        over.close();
        InputStream raw = fc.openInputStream();
        check("overwrite at 0 keeps tail", raw.read() == 0 && raw.read() == 5 && raw.read() == 'X');
        raw.close();
        fc.truncate(4);
        check("truncate", fc.fileSize() == 4);
        check("getName/getPath/getURL", fc.getName().equals("a b.txt") && fc.getPath().equals("/root1/iotest/")
            && fc.getURL().equals("file:///root1/iotest/a%20b.txt"));
        fc.rename("c.dat");
        check("rename", fc.getName().equals("c.dat") && fc.exists());
        FileConnection sub = (FileConnection) Connector.open("file:///root1/iotest/sub/");
        sub.mkdir();
        sub.close();
        Enumeration list = dir.list();
        String names = "";
        while (list.hasMoreElements()) names += list.nextElement() + ",";
        check("list " + names, names.indexOf("c.dat,") >= 0 && names.indexOf("sub/,") >= 0);
        check("list filter", !dir.list("*.txt", false).hasMoreElements());
        check("directorySize", dir.directorySize(false) == 4);
        fc.delete();
        check("delete", !fc.exists());
        try {
            Connector.open("file:///root1/../../escape.txt");
            FileConnection esc = (FileConnection) Connector.open("file:///root1/../../escape.txt");
            check("path cannot escape root (" + esc.getPath() + ")", esc.getPath().equals("/"));
        } catch (IllegalArgumentException e) {
            check("path cannot escape root", true);
        }

        // ---- tones
        byte[] seq = {ToneControl.VERSION, 1, ToneControl.TEMPO, 30, ToneControl.BLOCK_START, 0, 60, 8, 62, 8, ToneControl.BLOCK_END, 0,
            ToneControl.PLAY_BLOCK, 0, ToneControl.SET_VOLUME, 50, ToneControl.REPEAT, 2, 64, 16, ToneControl.SILENCE, 8};
        byte[] wav = j4i.ToneSeq.toWav(seq);
        // 2x 1/8 note + 2x 1/4 note + 1/8 rest at 120bpm = 250+250+500+500+250 ms = 1.75 s at 22050 Hz 16-bit
        int samples = (wav.length - 44) / 2;
        check("tone sequence length " + samples, Math.abs(samples - 22050 * 175 / 100) < 50 && wav[0] == 'R' && wav[8] == 'W');
        Player p = Manager.createPlayer(Manager.TONE_DEVICE_LOCATOR);
        p.realize();
        ((ToneControl) p.getControl("ToneControl")).setSequence(seq);
        p.start();
        check("tone player started", p.getState() == Player.STARTED);
        p.close();
        try {
            j4i.ToneSeq.toWav(new byte[] {ToneControl.VERSION, 1, ToneControl.PLAY_BLOCK, 3});
            check("undefined block rejected", false);
        } catch (IllegalArgumentException e) {
            check("undefined block rejected", true);
        }
        // Nokia OTT: 2 commands, basic song, 1 pattern with 1 note (A, 1/4) at default tempo 63 bpm
        byte[] ott = ott();
        byte[] nw = j4i.NokiaTone.toWav(ott);
        check("nokia ringing tone parsed", nw != null && nw.length > 44);
        com.nokia.mid.sound.Sound s = new com.nokia.mid.sound.Sound(ott, com.nokia.mid.sound.Sound.FORMAT_TONE);
        check("nokia sound init", s.getState() == com.nokia.mid.sound.Sound.SOUND_STOPPED);
        s.release();

        // ---- inflate (zlib stream of "hello hello hello")
        byte[] z = {120, -100, -53, 72, -51, -55, -55, 87, -56, 64, -112, 0};
        byte[] plain = j4i.Sys.inflate(z, 0, z.length, 0);
        check("inflate", plain != null && new String(plain).equals("hello hello hello"));

        // ---- http (only when asked: needs network)
        // ---- built-in MIDI synth: 120 bpm (default tempo), 480 ticks/quarter, piano C-E-G quarters + a kick drum
        java.io.ByteArrayOutputStream trk = new java.io.ByteArrayOutputStream();
        int[] melody = {60, 64, 67};
        trk.write(0); trk.write(0xC0); trk.write(0); // program 0
        trk.write(0); trk.write(0x99); trk.write(36); trk.write(110); // kick on channel 10
        for (int i = 0; i < 3; i++) {
            trk.write(0);
            trk.write(0x90); trk.write(melody[i]); trk.write(100);
            trk.write(0x83); trk.write(0x60); // delta 480
            trk.write(0x80); trk.write(melody[i]); trk.write(0);
        }
        trk.write(0); trk.write(0xFF); trk.write(0x2F); trk.write(0);
        byte[] tb = trk.toByteArray();
        java.io.ByteArrayOutputStream mid = new java.io.ByteArrayOutputStream();
        mid.write(new byte[] {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0x01, (byte) 0xE0, 'M', 'T', 'r', 'k', 0, 0, 0, (byte) tb.length}, 0, 22);
        mid.write(tb, 0, tb.length);
        byte[] midWav = j4i.Sys.renderMidi(mid.toByteArray());
        int midSamples = midWav == null ? 0 : (midWav.length - 44) / 2;
        int peak = 0;
        for (int i = 44; midWav != null && i + 1 < midWav.length; i += 2) {
            int smp = (short) ((midWav[i] & 255) | (midWav[i + 1] << 8));
            if (Math.abs(smp) > peak) peak = Math.abs(smp);
        }
        // 3 quarter notes at 120 bpm = 1.5 s
        check("midi render " + midSamples + " samples, peak " + peak, Math.abs(midSamples - 22050 * 155 / 100) < 2000 && peak > 3000);
        check("midi garbage rejected", j4i.Sys.renderMidi(new byte[] {1, 2, 3}) == null);
        javax.microedition.media.Player mp = Manager.createPlayer(new java.io.ByteArrayInputStream(mid.toByteArray()), "audio/midi");
        mp.start();
        check("midi player started", mp.getState() == Player.STARTED);
        mp.close();

        if (args.length > 0 && args[0].equals("net")) {
            HttpConnection hc = (HttpConnection) Connector.open("http://example.com/");
            int code = hc.getResponseCode();
            InputStream hin = hc.openInputStream();
            byte[] buf = new byte[4096];
            int total = 0, n;
            while ((n = hin.read(buf, 0, buf.length)) > 0) total += n;
            hc.close();
            check("http GET example.com code=" + code + " bytes=" + total, code == 200 && total > 100);
        }
        System.out.println(failures == 0 ? "ALL OK" : failures + " FAILURES");
    }

    /** Builds a tiny OTT tone bit by bit. */
    static byte[] ott() {
        Bits b = new Bits();
        b.put(2, 8); // command length
        b.put(0x25, 7); b.align(); // ringing tone programming
        b.put(0x1D, 7); // sound
        b.put(1, 3); // basic song
        b.put(0, 4); // empty title
        b.put(1, 8); // one pattern
        b.put(0, 3); b.put(0, 2); b.put(0, 4); // pattern header A, no loop
        b.put(2, 8); // 2 instructions
        b.put(4, 3); b.put(12, 5); // tempo 100 bpm
        b.put(1, 3); b.put(10, 4); b.put(2, 3); b.put(0, 2); // note A quarter
        return b.bytes();
    }

    static class Bits {
        byte[] d = new byte[64];
        int bit;

        void put(int v, int n) {
            for (int i = n - 1; i >= 0; i--) {
                if (((v >> i) & 1) != 0) d[bit >> 3] |= (byte) (0x80 >> (bit & 7));
                bit++;
            }
        }

        void align() { bit = (bit + 7) & ~7; }

        byte[] bytes() {
            byte[] r = new byte[(bit + 7) >> 3];
            System.arraycopy(d, 0, r, 0, r.length);
            return r;
        }
    }
}
