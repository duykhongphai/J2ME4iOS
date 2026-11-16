package javax.microedition.lcdui;

public class ChoiceGroup extends Item implements Choice {
    private final ChoiceModel model;

    public ChoiceGroup(String label, int choiceType) { this(label, choiceType, new String[0], null); }

    public ChoiceGroup(String label, int choiceType, String[] stringElements, Image[] imageElements) {
        super(label);
        if (choiceType != EXCLUSIVE && choiceType != MULTIPLE && choiceType != POPUP) throw new IllegalArgumentException();
        if (stringElements == null) throw new NullPointerException();
        model = new ChoiceModel(choiceType);
        for (int i = 0; i < stringElements.length; i++) model.append(stringElements[i], imageElements != null ? imageElements[i] : null);
    }

    public int size() { return model.size(); }

    public String getString(int i) { return model.getString(i); }

    public Image getImage(int i) { return model.getImage(i); }

    public int append(String s, Image img) {
        int r = model.append(s, img);
        repaintOwner();
        return r;
    }

    public void insert(int i, String s, Image img) {
        model.insert(i, s, img);
        repaintOwner();
    }

    public void delete(int i) {
        model.delete(i);
        repaintOwner();
    }

    public void deleteAll() {
        model.deleteAll();
        repaintOwner();
    }

    public void set(int i, String s, Image img) {
        model.set(i, s, img);
        repaintOwner();
    }

    public boolean isSelected(int i) { return model.isSelected(i); }

    public int getSelectedIndex() { return model.getSelectedIndex(); }

    public int getSelectedFlags(boolean[] a) { return model.getSelectedFlags(a); }

    public void setSelectedIndex(int i, boolean s) {
        model.setSelectedIndex(i, s);
        repaintOwner();
    }

    public void setSelectedFlags(boolean[] a) {
        model.setSelectedFlags(a);
        repaintOwner();
    }

    public void setFitPolicy(int p) { model.fit = p; }

    public int getFitPolicy() { return model.fit; }

    public void setFont(int i, Font f) { model.setFont(i, f); }

    public Font getFont(int i) { return model.getFont(i); }

    int contentHeight(int w) { return model.height(w); }

    void paintContent(Graphics g, int x, int y, int w, boolean focused) { model.paint(g, x, y, w, focused); }

    boolean j4iFocusable() { return true; }

    boolean j4iMoveInside(int dir) {
        if (model.type == POPUP) return false;
        return model.move(dir, false);
    }

    boolean j4iKey(int code) {
        if (Screen.gameAction(code) == Canvas.FIRE) {
            model.activate();
            notifyStateChanged();
            return true;
        }
        return super.j4iKey(code);
    }

    void j4iTap(int x, int y, int w) {
        if (model.type != POPUP) {
            int idx = model.indexAt(y - labelHeight(), w);
            if (idx < 0) return;
            model.focus = idx;
        }
        model.activate();
        notifyStateChanged();
    }
}
