package javax.microedition.lcdui;

public final class Font {
    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_LARGE = 16;
    public static final int FACE_SYSTEM = 0;
    public static final int FACE_MONOSPACE = 32;
    public static final int FACE_PROPORTIONAL = 64;
    public static final int FONT_STATIC_TEXT = 0;
    public static final int FONT_INPUT_TEXT = 1;

    private static Font[] cache = new Font[72];
    private static Font defaultFont;

    final int face, style, size;
    final int px;
    private final int ascent, height;

    private Font(int face, int style, int size) {
        this.face = face;
        this.style = style;
        this.size = size;
        this.px = pixelSize(size);
        int[] info = new int[3];
        j4i.Gfx.fontInfo(px, style, info);
        ascent = info[0];
        height = info[2];
    }

    static int pixelSize(int size) {
        int base = size == SIZE_SMALL ? 12 : size == SIZE_LARGE ? 16 : 14;
        int sw = Math.min(j4i.Sys.screenWidth(), j4i.Sys.screenHeight());
        int px = base * sw / 240;
        if (sw > 240) px = base + (px - base) / 2;
        return px < 9 ? 9 : px;
    }

    public static Font getDefaultFont() {
        if (defaultFont == null) defaultFont = getFont(FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM);
        return defaultFont;
    }

    public static Font getFont(int fontSpecifier) {
        if (fontSpecifier != FONT_STATIC_TEXT && fontSpecifier != FONT_INPUT_TEXT) throw new IllegalArgumentException();
        return getDefaultFont();
    }

    public static synchronized Font getFont(int face, int style, int size) {
        if ((face != FACE_SYSTEM && face != FACE_MONOSPACE && face != FACE_PROPORTIONAL) || (style & ~7) != 0
            || (size != SIZE_SMALL && size != SIZE_MEDIUM && size != SIZE_LARGE)) throw new IllegalArgumentException();
        int key = (face >> 5) * 24 + style * 3 + (size == SIZE_SMALL ? 0 : size == SIZE_MEDIUM ? 1 : 2);
        Font f = cache[key];
        if (f == null) {
            f = new Font(face, style, size);
            cache[key] = f;
        }
        return f;
    }

    public int getStyle() { return style; }

    public int getSize() { return size; }

    public int getFace() { return face; }

    public boolean isPlain() { return style == STYLE_PLAIN; }

    public boolean isBold() { return (style & STYLE_BOLD) != 0; }

    public boolean isItalic() { return (style & STYLE_ITALIC) != 0; }

    public boolean isUnderlined() { return (style & STYLE_UNDERLINED) != 0; }

    public int getHeight() { return height; }

    public int getBaselinePosition() { return ascent; }

    public int charWidth(char ch) {
        char[] c = {ch};
        return j4i.Gfx.charsWidth(c, 0, 1, px, style);
    }

    public int charsWidth(char[] ch, int offset, int length) {
        if (ch == null) throw new NullPointerException();
        return j4i.Gfx.charsWidth(ch, offset, length, px, style);
    }

    public int stringWidth(String str) {
        if (str == null) throw new NullPointerException();
        return j4i.Gfx.stringWidth(str, 0, str.length(), px, style);
    }

    public int substringWidth(String str, int offset, int len) {
        if (str == null) throw new NullPointerException();
        return j4i.Gfx.stringWidth(str, offset, len, px, style);
    }
}
