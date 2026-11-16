package javax.microedition.lcdui;

public class TextField extends Item implements TextInput.Target {
    public static final int ANY = 0;
    public static final int EMAILADDR = 1;
    public static final int NUMERIC = 2;
    public static final int PHONENUMBER = 3;
    public static final int URL = 4;
    public static final int DECIMAL = 5;
    public static final int PASSWORD = 0x10000;
    public static final int UNEDITABLE = 0x20000;
    public static final int SENSITIVE = 0x40000;
    public static final int NON_PREDICTIVE = 0x80000;
    public static final int INITIAL_CAPS_WORD = 0x100000;
    public static final int INITIAL_CAPS_SENTENCE = 0x200000;
    public static final int CONSTRAINT_MASK = 0xFFFF;

    private StringBuffer text = new StringBuffer();
    private int maxSize;
    private int constraints;

    public TextField(String label, String text, int maxSize, int constraints) {
        super(label);
        if (maxSize <= 0) throw new IllegalArgumentException();
        this.maxSize = maxSize;
        this.constraints = constraints;
        if (text != null) setString(text);
    }

    public String getString() { return text.toString(); }

    public void setString(String s) {
        if (s != null && s.length() > maxSize) throw new IllegalArgumentException();
        text.setLength(0);
        if (s != null) text.append(s);
        repaintOwner();
    }

    public int getChars(char[] data) {
        if (data.length < text.length()) throw new ArrayIndexOutOfBoundsException();
        text.getChars(0, text.length(), data, 0);
        return text.length();
    }

    public void setChars(char[] data, int offset, int length) {
        setString(data == null ? null : new String(data, offset, length));
    }

    public void insert(String src, int position) {
        if (text.length() + src.length() > maxSize) throw new IllegalArgumentException();
        position = Math.max(0, Math.min(position, text.length()));
        text.insert(position, src);
        repaintOwner();
    }

    public void insert(char[] data, int offset, int length, int position) { insert(new String(data, offset, length), position); }

    public void delete(int offset, int length) {
        text.delete(offset, offset + length);
        repaintOwner();
    }

    public int getMaxSize() { return maxSize; }

    public int setMaxSize(int m) {
        if (m <= 0) throw new IllegalArgumentException();
        maxSize = m;
        if (text.length() > m) text.setLength(m);
        return m;
    }

    public int size() { return text.length(); }

    public int getCaretPosition() { return text.length(); }

    public void setConstraints(int c) {
        constraints = c;
        repaintOwner();
    }

    public int getConstraints() { return constraints; }

    public void setInitialInputMode(String characterSubset) {}

    public void j4iSetText(String s) {
        if (s.length() > maxSize) s = s.substring(0, maxSize);
        if (!TextInput.valid(s, constraints)) return;
        setString(s);
        notifyStateChanged();
    }

    int contentHeight(int w) { return Font.getDefaultFont().getHeight() + 6; }

    void paintContent(Graphics g, int x, int y, int w, boolean focused) {
        Font f = Font.getDefaultFont();
        int h = f.getHeight() + 5;
        g.setColor((constraints & UNEDITABLE) != 0 ? 0xE8E8E8 : 0xFFFFFF);
        g.fillRect(x, y, w, h);
        g.setColor(focused ? Screen.HL_BG : Screen.BORDER);
        g.drawRect(x, y, w - 1, h - 1);
        if (focused) g.drawRect(x + 1, y + 1, w - 3, h - 3);
        g.setColor(Screen.FG);
        g.setFont(f);
        g.drawString(Screen.clipText(TextInput.display(text.toString(), constraints), f, w - 8), x + 4, y + 3, Graphics.LEFT | Graphics.TOP);
    }

    boolean j4iFocusable() { return true; }

    boolean j4iKey(int code) {
        if ((constraints & UNEDITABLE) != 0) return super.j4iKey(code);
        int c = constraints & CONSTRAINT_MASK;
        if ((c == NUMERIC || c == PHONENUMBER || c == DECIMAL) && ((code >= '0' && code <= '9') || code == '*' || code == '#')) {
            if (text.length() < maxSize && code >= '0' && code <= '9') {
                text.append((char) code);
                notifyStateChanged();
            }
            return true;
        }
        if (code == -8) {
            if (text.length() > 0) {
                text.setLength(text.length() - 1);
                notifyStateChanged();
            }
            return true;
        }
        if (Screen.gameAction(code) == Canvas.FIRE) {
            TextInput.request(this, label != null ? label : "", text.toString(), maxSize, constraints);
            return true;
        }
        return false;
    }
}
