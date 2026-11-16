package javax.microedition.lcdui;

public class ImageItem extends Item {
    private Image img;
    private String altText;
    private int appearance;

    public ImageItem(String label, Image img, int layout, String altText) { this(label, img, layout, altText, PLAIN); }

    public ImageItem(String label, Image image, int layout, String altText, int appearanceMode) {
        super(label);
        this.img = image;
        this.layout = layout;
        this.altText = altText;
        this.appearance = appearanceMode;
    }

    public Image getImage() { return img; }

    public void setImage(Image image) {
        img = image;
        repaintOwner();
    }

    public String getAltText() { return altText; }

    public void setAltText(String t) { altText = t; }

    public int getAppearanceMode() { return appearance; }

    int contentHeight(int w) { return img != null ? img.h : 0; }

    void paintContent(Graphics g, int x, int y, int w, boolean focused) {
        if (img == null) return;
        int h = layout & 3;
        int ix = h == LAYOUT_CENTER ? x + (w - img.w) / 2 : h == LAYOUT_RIGHT ? x + w - img.w : x;
        g.drawImage(img, ix, y, Graphics.LEFT | Graphics.TOP);
        if (focused) {
            g.setColor(Screen.HL_BG);
            g.drawRect(ix - 1, y - 1, img.w + 1, img.h + 1);
        }
    }

    boolean j4iFocusable() { return appearance != PLAIN || !cmds.isEmpty(); }
}
