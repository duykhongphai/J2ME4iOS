package j4i.net;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.microedition.io.ConnectionNotFoundException;
import javax.microedition.io.InputConnection;

/** "resource:/path" - read-only access to jar resources. */
public final class ResourceConn implements InputConnection {
    private final byte[] data;

    public ResourceConn(String url) throws IOException {
        String p = url.substring(url.indexOf(':') + 1);
        while (p.startsWith("/")) p = p.substring(1);
        data = j4i.Sys.getResource(p);
        if (data == null) throw new ConnectionNotFoundException("Resource not found: " + url);
    }

    public InputStream openInputStream() { return new ByteArrayInputStream(data); }

    public DataInputStream openDataInputStream() { return new DataInputStream(openInputStream()); }

    public void close() {}
}
