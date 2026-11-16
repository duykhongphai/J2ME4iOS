package javax.microedition.lcdui;

import java.util.Vector;

public class StringItem extends Item {
    private String text;
    private int appearance;
    private Font font;

    public StringItem(String label, String text) { this(label, text, PLAIN); }

    public StringItem(String label, String text, int appearanceMode) {
        super(label);
        if (appearanceMode < PLAIN || appearanceMode > BUTTON) throw new IllegalArgumentException();
        this.text = text;
        this.appearance = appearanceMode;
    }

    public String getText() { return text; }

    public void setText(String t) {
        text = t;
        repaintOwner();
    }

    public int getAppearanceMode() { return appearance; }

    public void setFont(Font f) {
        font = f;
        repaintOwner();
    }

    public Font getFont() { return font != null ? font : Font.getDefaultFont(); }

    int contentHeight(int w) {
        if (text == null || text.length() == 0) return 0;
        Font f = getFont();
        if (appearance == BUTTON) return f.getHeight() + 6;
        return Screen.wrap(text, f, w).size() * f.getHeight();
    }

    void paintContent(Graphics g, int x, int y, int w, boolean focused) {
        if (text == null) return;
        Font f = getFont();
        g.setFont(f);
        if (appearance == BUTTON) {
            int bw = Math.min(w, f.stringWidth(text) + 12);
            g.setColor(focused ? Screen.HL_BG : 0xE0E0E0);
            g.fillRoundRect(x, y, bw, f.getHeight() + 5, 6, 6);
            g.setColor(focused ? Screen.HL_FG : Screen.FG);
            g.drawString(text, x + 6, y + 3, Graphics.LEFT | Graphics.TOP);
            return;
        }
        Vector lines = Screen.wrap(text, f, w);
        int fg = appearance == HYPERLINK ? 0x0040C0 : Screen.FG;
        if (focused) {
            g.setColor(0xD0E0FF);
            g.fillRect(x - 2, y, w + 4, lines.size() * f.getHeight());
        }
        g.setColor(fg);
        for (int i = 0; i < lines.size(); i++) {
            String s = (String) lines.elementAt(i);
            g.drawString(s, x, y + i * f.getHeight(), Graphics.LEFT | Graphics.TOP);
            if (appearance == HYPERLINK) g.drawLine(x, y + (i + 1) * f.getHeight() - 1, x + f.stringWidth(s), y + (i + 1) * f.getHeight() - 1);
        }
    }

    boolean j4iFocusable() { return appearance != PLAIN || !cmds.isEmpty(); }
}
