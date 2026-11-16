package javax.wireless.messaging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MessagePart {
    private final byte[] content;
    private final String mimeType, contentId, contentLocation, encoding;

    public MessagePart(byte[] contents, int offset, int length, String mimeType, String contentId, String contentLocation, String enc)
        throws SizeExceededException {
        content = new byte[length];
        System.arraycopy(contents, offset, content, 0, length);
        this.mimeType = mimeType;
        this.contentId = contentId;
        this.contentLocation = contentLocation;
        encoding = enc;
    }

    public MessagePart(byte[] contents, String mimeType, String contentId, String contentLocation, String enc) throws SizeExceededException {
        this(contents, 0, contents.length, mimeType, contentId, contentLocation, enc);
    }

    public MessagePart(InputStream is, String mimeType, String contentId, String contentLocation, String enc)
        throws IOException, SizeExceededException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[1024];
        int n;
        while ((n = is.read(b, 0, b.length)) > 0) bo.write(b, 0, n);
        content = bo.toByteArray();
        this.mimeType = mimeType;
        this.contentId = contentId;
        this.contentLocation = contentLocation;
        encoding = enc;
    }

    public byte[] getContent() { return content; }

    public InputStream getContentAsStream() { return new ByteArrayInputStream(content); }

    public String getContentID() { return contentId; }

    public String getContentLocation() { return contentLocation; }

    public String getEncoding() { return encoding; }

    public int getLength() { return content.length; }

    public String getMIMEType() { return mimeType; }
}
