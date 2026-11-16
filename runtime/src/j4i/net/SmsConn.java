package j4i.net;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Date;
import java.util.Vector;
import javax.wireless.messaging.BinaryMessage;
import javax.wireless.messaging.Message;
import javax.wireless.messaging.MessageConnection;
import javax.wireless.messaging.MessageListener;
import javax.wireless.messaging.MessagePart;
import javax.wireless.messaging.MultipartMessage;
import javax.wireless.messaging.TextMessage;

/** SMS/MMS are not available: messages can be built, but sending fails with IOException. */
public final class SmsConn implements MessageConnection {
    private final String address;
    private boolean closed;

    public SmsConn(String url) { address = url; }

    public Message newMessage(String type) {
        boolean server = address.startsWith("sms://:") || address.startsWith("mms://:");
        return newMessage(type, server ? null : address);
    }

    public Message newMessage(String type, String addr) {
        if (TEXT_MESSAGE.equals(type)) return new Text(addr);
        if (BINARY_MESSAGE.equals(type)) return new Binary(addr);
        if (MULTIPART_MESSAGE.equals(type)) return new Multi(addr);
        throw new IllegalArgumentException("Unknown message type " + type);
    }

    public void send(Message msg) throws IOException, InterruptedIOException {
        if (closed) throw new IOException("Connection closed");
        if (msg == null) throw new NullPointerException();
        System.out.println("SMS send blocked: " + msg.getAddress());
        throw new IOException("Sending messages is not supported");
    }

    public Message receive() throws IOException, InterruptedIOException {
        if (closed) throw new IOException("Connection closed");
        throw new InterruptedIOException("No messages");
    }

    public void setMessageListener(MessageListener l) throws IOException {
        if (closed) throw new IOException("Connection closed");
    }

    public int numberOfSegments(Message msg) {
        if (msg instanceof TextMessage) {
            String s = ((TextMessage) msg).getPayloadText();
            int n = s == null ? 0 : s.length();
            return n <= 160 ? 1 : (n + 152) / 153;
        }
        if (msg instanceof BinaryMessage) {
            byte[] d = ((BinaryMessage) msg).getPayloadData();
            int n = d == null ? 0 : d.length;
            return n <= 140 ? 1 : (n + 132) / 133;
        }
        return 1;
    }

    public void close() { closed = true; }

    static class Base implements Message {
        String addr;

        Base(String a) { addr = a; }

        public String getAddress() { return addr; }

        public void setAddress(String a) { addr = a; }

        public Date getTimestamp() { return null; }
    }

    static final class Text extends Base implements TextMessage {
        private String text;

        Text(String a) { super(a); }

        public String getPayloadText() { return text; }

        public void setPayloadText(String d) { text = d; }
    }

    static final class Binary extends Base implements BinaryMessage {
        private byte[] data;

        Binary(String a) { super(a); }

        public byte[] getPayloadData() { return data; }

        public void setPayloadData(byte[] d) { data = d; }
    }

    static final class Multi extends Base implements MultipartMessage {
        private final Vector parts = new Vector();
        private final Vector addrs = new Vector();
        private String subject, start;

        Multi(String a) { super(a); }

        public boolean addAddress(String type, String address) {
            addrs.addElement(address);
            return true;
        }

        public void addMessagePart(MessagePart part) { parts.addElement(part); }

        public String[] getAddresses(String type) {
            if (addrs.isEmpty()) return null;
            String[] r = new String[addrs.size()];
            addrs.copyInto(r);
            return r;
        }

        public String getHeader(String h) { return null; }

        public MessagePart getMessagePart(String id) {
            for (int i = 0; i < parts.size(); i++) {
                MessagePart p = (MessagePart) parts.elementAt(i);
                if (id != null && id.equals(p.getContentID())) return p;
            }
            return null;
        }

        public MessagePart[] getMessageParts() {
            MessagePart[] r = new MessagePart[parts.size()];
            parts.copyInto(r);
            return r;
        }

        public String getStartContentId() { return start; }

        public String getSubject() { return subject; }

        public boolean removeAddress(String type, String address) { return addrs.removeElement(address); }

        public void removeAddresses() { addrs.removeAllElements(); }

        public void removeAddresses(String type) { addrs.removeAllElements(); }

        public boolean removeMessagePart(MessagePart part) { return parts.removeElement(part); }

        public boolean removeMessagePartId(String id) {
            MessagePart p = getMessagePart(id);
            return p != null && parts.removeElement(p);
        }

        public boolean removeMessagePartLocation(String loc) { return false; }

        public void setHeader(String h, String v) {}

        public void setStartContentId(String id) { start = id; }

        public void setSubject(String s) { subject = s; }
    }
}
