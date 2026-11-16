package javax.microedition.lcdui;

import java.util.Vector;

/** Base for the high level UI (Form, List, Alert, TextBox). A simple, phone-like renderer. */
public abstract class Screen extends Displayable {
    static final int BG = 0xFFFFFF, FG = 0x000000, HL_BG = 0x2060C0, HL_FG = 0xFFFFFF, TITLE_BG = 0x204080, BORDER = 0x909090;

    int scroll;

    Screen() {}

    static Font titleFont() { return Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM); }

    static Font textFont() { return Font.getDefaultFont(); }

    static Font labelFont() { return Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL); }

    int headerHeight() {
        int h = 0;
        if (title != null) h += titleFont().getHeight() + 6;
        if (ticker != null) h += textFont().getHeight() + 2;
        return h;
    }

    int viewHeight() { return Display.H - headerHeight() - Display.softBarHeight(); }

    public int getWidth() { return Display.W; }

    public int getHeight() { return viewHeight(); }

    void j4iPaint(Graphics g) {
        int W = Display.W, H = Display.H;
        g.setColor(BG);
        g.fillRect(0, 0, W, H);
        int y = 0;
        if (title != null) {
            Font tf = titleFont();
            int th = tf.getHeight() + 6;
            g.setColor(TITLE_BG);
            g.fillRect(0, 0, W, th);
            g.setColor(0xFFFFFF);
            g.setFont(tf);
            g.drawString(clipText(title, tf, W - 8), W / 2, 3, Graphics.HCENTER | Graphics.TOP);
            y = th;
        }
        if (ticker != null) {
            Font f = textFont();
            g.setColor(0xFFF0C0);
            g.fillRect(0, y, W, f.getHeight() + 2);
            g.setColor(FG);
            g.setFont(f);
            g.drawString(ticker.getString(), 2, y + 1, Graphics.LEFT | Graphics.TOP);
            y += f.getHeight() + 2;
        }
        int vh = viewHeight();
        g.setClip(0, y, W, vh);
        g.translate(0, y);
        paintContent(g, W, vh);
        g.reset();
        int ch = contentHeight(W);
        if (ch > vh) {
            int bar = Math.max(10, vh * vh / ch);
            int by = y + (vh - bar) * scroll / Math.max(1, ch - vh);
            g.setColor(0xC0C0C0);
            g.fillRect(W - 3, by, 3, bar);
        }
        Display.paintSoftBar(g, this, false);
    }

    abstract void paintContent(Graphics g, int w, int h);

    int contentHeight(int w) { return 0; }

    void ensureVisible(int top, int bottom) {
        int vh = viewHeight();
        if (bottom - top > vh) bottom = top + vh;
        if (top < scroll) scroll = top;
        if (bottom > scroll + vh) scroll = bottom - vh;
        if (scroll < 0) scroll = 0;
    }

    void scrollBy(int dy) {
        int max = Math.max(0, contentHeight(Display.W) - viewHeight());
        scroll = Math.max(0, Math.min(max, scroll + dy));
        repaintSelf();
    }

    static int gameAction(int code) {
        switch (code) {
        case -1: case '2': return Canvas.UP;
        case -2: case '8': return Canvas.DOWN;
        case -3: case '4': return Canvas.LEFT;
        case -4: case '6': return Canvas.RIGHT;
        case -5: case '5': return Canvas.FIRE;
        }
        return 0;
    }

    static String clipText(String s, Font f, int w) {
        if (f.stringWidth(s) <= w) return s;
        int n = s.length();
        while (n > 0 && f.substringWidth(s, 0, n) + f.stringWidth("...") > w) n--;
        return s.substring(0, n) + "...";
    }

    static Vector wrap(String s, Font f, int w) {
        Vector lines = new Vector();
        if (s == null) return lines;
        int start = 0, n = s.length();
        while (start < n) {
            int nl = s.indexOf('\n', start);
            int end = nl < 0 ? n : nl;
            while (start <= end) {
                int len = end - start;
                if (f.substringWidth(s, start, len) <= w) {
                    lines.addElement(s.substring(start, end));
                    break;
                }
                int fit = len;
                while (fit > 1 && f.substringWidth(s, start, fit) > w) fit--;
                int brk = start + fit;
                int sp = s.lastIndexOf(' ', brk);
                if (sp > start) brk = sp;
                lines.addElement(s.substring(start, brk));
                start = brk;
                while (start < end && s.charAt(start) == ' ') start++;
            }
            start = end + 1;
            if (nl >= 0 && nl == n - 1) lines.addElement("");
        }
        return lines;
    }
}
