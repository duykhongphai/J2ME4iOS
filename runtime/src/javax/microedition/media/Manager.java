package javax.microedition.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class Manager {
    public static final String TONE_DEVICE_LOCATOR = "device://tone";
    public static final String MIDI_DEVICE_LOCATOR = "device://midi";

    private static final TimeBase SYSTEM_TB = new TimeBase() {
        public long getTime() { return System.currentTimeMillis() * 1000L; }
    };

    private Manager() {}

    public static String[] getSupportedContentTypes(String protocol) {
        return new String[] {"audio/x-wav", "audio/wav", "audio/midi", "audio/x-midi", "audio/mpeg", "audio/amr", "audio/x-tone-seq"};
    }

    public static String[] getSupportedProtocols(String contentType) { return new String[] {"device", "resource"}; }

    public static Player createPlayer(String locator) throws IOException, MediaException {
        if (locator == null) throw new IllegalArgumentException();
        if (locator.equals(TONE_DEVICE_LOCATOR)) return new PlayerImpl(null, "audio/x-tone-seq");
        if (locator.equals(MIDI_DEVICE_LOCATOR)) return new PlayerImpl(null, "audio/midi");
        String path = locator;
        if (path.startsWith("resource:")) path = path.substring(9);
        if (path.startsWith("/")) path = path.substring(1);
        byte[] data = j4i.Sys.getResource(path);
        if (data == null) throw new MediaException("Cannot create player for " + locator);
        return new PlayerImpl(data, guessType(locator, data));
    }

    public static Player createPlayer(InputStream stream, String type) throws IOException, MediaException {
        if (stream == null) throw new IllegalArgumentException();
        ByteArrayOutputStream bo = new ByteArrayOutputStream(8192);
        byte[] buf = new byte[4096];
        int n;
        while ((n = stream.read(buf, 0, buf.length)) > 0) bo.write(buf, 0, n);
        byte[] data = bo.toByteArray();
        return new PlayerImpl(data, type != null ? type : guessType("", data));
    }

    static String guessType(String name, byte[] d) {
        if (d.length >= 4) {
            if (d[0] == 'M' && d[1] == 'T' && d[2] == 'h' && d[3] == 'd') return "audio/midi";
            if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F') return "audio/x-wav";
            if (d[0] == '#' && d[1] == '!' && d[2] == 'A' && d[3] == 'M') return "audio/amr";
            if ((d[0] == 'I' && d[1] == 'D' && d[2] == '3') || ((d[0] & 0xFF) == 0xFF && (d[1] & 0xE0) == 0xE0)) return "audio/mpeg";
        }
        String n = name.toLowerCase();
        if (n.endsWith(".mid") || n.endsWith(".midi")) return "audio/midi";
        if (n.endsWith(".wav")) return "audio/x-wav";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".amr")) return "audio/amr";
        return "application/octet-stream";
    }

    public static void playTone(int note, int duration, int volume) throws MediaException {
        if (note < 0 || note > 127 || duration <= 0) throw new IllegalArgumentException();
        j4i.Sys.tone(note, duration, Math.max(0, Math.min(100, volume)));
    }

    public static TimeBase getSystemTimeBase() { return SYSTEM_TB; }

    /** Polls started players for end of media; true while any is playing. */
    public static boolean j4iActive() { return PlayerImpl.poll(); }
}
