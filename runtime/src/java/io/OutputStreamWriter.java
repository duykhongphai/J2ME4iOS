package java.io;

public class OutputStreamWriter extends Writer {
    private OutputStream out;
    private String enc;

    public OutputStreamWriter(OutputStream os) { out = os; }

    public OutputStreamWriter(OutputStream os, String enc) throws UnsupportedEncodingException {
        if (enc == null) throw new NullPointerException();
        j4i.Encoding.code(enc);
        out = os;
        this.enc = enc;
    }

    public void write(char[] cbuf, int off, int len) throws IOException {
        out.write(j4i.Encoding.encode(cbuf, off, len, enc));
    }

    public void flush() throws IOException { out.flush(); }

    public void close() throws IOException { out.close(); }
}
