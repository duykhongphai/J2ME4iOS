package javax.microedition.lcdui;

import java.util.Vector;

public class TextBox extends Screen implements TextInput.Target {
    private final StringBuffer text = new StringBuffer();
    private int maxSize;
    private int constraints;

    public TextBox(String title, String text, int maxSize, int constraints) {
        if (maxSize <= 0) throw new IllegalArgumentException();
        this.title = title;
        this.maxSize = maxSize;
        this.constraints = constraints;
        if (text != null) setString(text);
    }

    public String getString() { return text.toString(); }

    public void setString(String s) {
        if (s != null && s.length() > maxSize) throw new IllegalArgumentException();
        text.setLength(0);
        if (s != null) text.append(s);
        repaintSelf();
    }

    public int getChars(char[] data) {
        if (data.length < text.length()) throw new ArrayIndexOutOfBoundsException();
        text.getChars(0, text.length(), data, 0);
        return text.length();
    }

    public void setChars(char[] data, int offset, int length) { setString(data == null ? null : new String(data, offset, length)); }

    public void insert(String src, int position) {
        if (text.length() + src.length() > maxSize) throw new IllegalArgumentException();
        position = Math.max(0, Math.min(position, text.length()));
        text.insert(position, src);
        repaintSelf();
    }

    public void insert(char[] data, int offset, int length, int position) { insert(new String(data, offset, length), position); }

    public void delete(int offset, int length) {
        text.delete(offset, offset + length);
        repaintSelf();
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

    public void setConstraints(int c) { constraints = c; }

    public int getConstraints() { return constraints; }

    public void setInitialInputMode(String characterSubset) {}

    public void j4iSetText(String s) {
        if (s.length() > maxSize) s = s.substring(0, maxSize);
        if (!TextInput.valid(s, constraints)) return;
        setString(s);
    }

    void j4iShow() {
        if ((constraints & TextField.UNEDITABLE) == 0 && text.length() == 0) edit();
    }

    private void edit() { TextInput.request(this, title != null ? title : "", text.toString(), maxSize, constraints); }

    int contentHeight(int w) {
        Font f = textFont();
        return Screen.wrap(TextInput.display(text.toString(), constraints), f, w - 12).size() * f.getHeight() + 12;
    }

    void paintContent(Graphics g, int w, int h) {
        Font f = textFont();
        g.translate(0, -scroll);
        g.setColor(Screen.BORDER);
        g.drawRect(2, 2, w - 5, Math.max(h - 5, contentHeight(w) - 5));
        g.setColor(Screen.FG);
        g.setFont(f);
        Vector lines = Screen.wrap(TextInput.display(text.toString(), constraints), f, w - 12);
        for (int i = 0; i < lines.size(); i++) g.drawString((String) lines.elementAt(i), 6, 6 + i * f.getHeight(), Graphics.LEFT | Graphics.TOP);
    }

    void j4iKey(int type, int code) {
        if (type != Display.EV_KEY_PRESSED) return;
        int ga = gameAction(code);
        if (code == -8 && text.length() > 0) {
            text.setLength(text.length() - 1);
            repaintSelf();
        } else if (ga == Canvas.FIRE && (constraints & TextField.UNEDITABLE) == 0) {
            edit();
        } else if (ga == Canvas.UP || ga == Canvas.DOWN) {
            scrollBy((ga == Canvas.UP ? -1 : 1) * textFont().getHeight());
        }
    }

    void j4iPointer(int type, int x, int y) {
        if (type == Display.EV_POINTER_RELEASED && (constraints & TextField.UNEDITABLE) == 0) edit();
    }
}
