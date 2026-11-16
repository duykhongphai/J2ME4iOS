package javax.microedition.lcdui;

public class List extends Screen implements Choice {
    public static final Command SELECT_COMMAND = new Command("", Command.SCREEN, 0);

    final ChoiceModel model;
    private Command selectCommand = SELECT_COMMAND;

    public List(String title, int listType) { this(title, listType, new String[0], null); }

    public List(String title, int listType, String[] stringElements, Image[] imageElements) {
        if (listType != IMPLICIT && listType != EXCLUSIVE && listType != MULTIPLE) throw new IllegalArgumentException();
        if (stringElements == null) throw new NullPointerException();
        if (imageElements != null && imageElements.length != stringElements.length) throw new IllegalArgumentException();
        this.title = title;
        model = new ChoiceModel(listType);
        for (int i = 0; i < stringElements.length; i++) append(stringElements[i], imageElements != null ? imageElements[i] : null);
    }

    public int size() { return model.size(); }

    public String getString(int i) { return model.getString(i); }

    public Image getImage(int i) { return model.getImage(i); }

    public int append(String s, Image img) {
        int r = model.append(s, img);
        repaintSelf();
        return r;
    }

    public void insert(int i, String s, Image img) {
        model.insert(i, s, img);
        repaintSelf();
    }

    public void delete(int i) {
        model.delete(i);
        repaintSelf();
    }

    public void deleteAll() {
        model.deleteAll();
        scroll = 0;
        repaintSelf();
    }

    public void set(int i, String s, Image img) {
        model.set(i, s, img);
        repaintSelf();
    }

    public boolean isSelected(int i) { return model.isSelected(i); }

    public int getSelectedIndex() { return model.getSelectedIndex(); }

    public int getSelectedFlags(boolean[] a) { return model.getSelectedFlags(a); }

    public void setSelectedIndex(int i, boolean s) {
        model.setSelectedIndex(i, s);
        if (s && model.type != MULTIPLE) model.focus = i;
        repaintSelf();
    }

    public void setSelectedFlags(boolean[] a) {
        model.setSelectedFlags(a);
        repaintSelf();
    }

    public void setFitPolicy(int p) { model.fit = p; }

    public int getFitPolicy() { return model.fit; }

    public void setFont(int i, Font f) { model.setFont(i, f); }

    public Font getFont(int i) { return model.getFont(i); }

    public void setSelectCommand(Command command) {
        if (model.type != IMPLICIT) return;
        if (selectCommand != null && selectCommand != SELECT_COMMAND) removeCommand(selectCommand);
        selectCommand = command;
        if (command != null && command != SELECT_COMMAND) addCommand(command);
    }

    public void removeCommand(Command cmd) {
        if (cmd == selectCommand) selectCommand = null;
        super.removeCommand(cmd);
    }

    int contentHeight(int w) { return model.height(w); }

    void paintContent(Graphics g, int w, int h) {
        g.translate(0, -scroll);
        model.paint(g, 0, 0, w, true);
    }

    private void activate() {
        int f = model.focus;
        if (f < 0 || f >= model.size()) return;
        if (model.type == IMPLICIT) {
            model.setSelectedIndex(f, true);
            repaintSelf();
            if (selectCommand != null && listener != null) Display.instance.fireCommand(this, selectCommand);
        } else {
            model.activate();
            repaintSelf();
        }
    }

    void j4iKey(int type, int code) {
        if (type == Display.EV_KEY_RELEASED) return;
        int ga = gameAction(code);
        if (ga == Canvas.UP || ga == Canvas.DOWN) {
            model.move(ga == Canvas.UP ? -1 : 1, true);
            int[] r = model.rowBounds(model.focus, Display.W);
            ensureVisible(r[0], r[1]);
            repaintSelf();
        } else if (ga == Canvas.FIRE && type == Display.EV_KEY_PRESSED) {
            activate();
        }
    }

    void j4iPointer(int type, int x, int y) {
        if (type != Display.EV_POINTER_RELEASED) return;
        int idx = model.indexAt(y - headerHeight() + scroll, Display.W);
        if (idx >= 0) {
            model.focus = idx;
            activate();
        }
    }
}
