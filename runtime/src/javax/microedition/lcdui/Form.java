package javax.microedition.lcdui;

import java.util.Vector;

public class Form extends Screen {
    final Vector items = new Vector();
    ItemStateListener isl;
    int focus = -1;

    public Form(String title) { this.title = title; }

    public Form(String title, Item[] list) {
        this.title = title;
        if (list != null) for (int i = 0; i < list.length; i++) append(list[i]);
    }

    public int append(Item item) {
        if (item == null) throw new NullPointerException();
        if (item.owner != null) throw new IllegalStateException();
        item.owner = this;
        items.addElement(item);
        fixFocus();
        repaintSelf();
        return items.size() - 1;
    }

    public int append(String str) {
        if (str == null) throw new NullPointerException();
        return append(new StringItem(null, str));
    }

    public int append(Image img) {
        if (img == null) throw new NullPointerException();
        return append(new ImageItem(null, img, Item.LAYOUT_DEFAULT, null));
    }

    public void insert(int itemNum, Item item) {
        if (item == null) throw new NullPointerException();
        if (item.owner != null) throw new IllegalStateException();
        if (itemNum < 0 || itemNum > items.size()) throw new IndexOutOfBoundsException();
        item.owner = this;
        items.insertElementAt(item, itemNum);
        if (focus >= itemNum) focus++;
        fixFocus();
        repaintSelf();
    }

    public void delete(int itemNum) {
        Item it = (Item) items.elementAt(itemNum);
        it.owner = null;
        items.removeElementAt(itemNum);
        if (focus >= items.size()) focus = items.size() - 1;
        fixFocus();
        repaintSelf();
    }

    public void deleteAll() {
        for (int i = 0; i < items.size(); i++) ((Item) items.elementAt(i)).owner = null;
        items.removeAllElements();
        focus = -1;
        scroll = 0;
        repaintSelf();
    }

    public void set(int itemNum, Item item) {
        if (item == null) throw new NullPointerException();
        if (item.owner != null) throw new IllegalStateException();
        Item old = (Item) items.elementAt(itemNum);
        old.owner = null;
        item.owner = this;
        items.setElementAt(item, itemNum);
        fixFocus();
        repaintSelf();
    }

    public Item get(int itemNum) { return (Item) items.elementAt(itemNum); }

    public void setItemStateListener(ItemStateListener l) { isl = l; }

    public int size() { return items.size(); }

    public int getWidth() { return Display.W - 8; }

    public int getHeight() { return viewHeight(); }

    void stateChanged(Item it) {
        if (isl != null) {
            try {
                isl.itemStateChanged(it);
            } catch (Throwable t) {
                j4i.MidletRunner.report("itemStateChanged", t);
            }
        }
    }

    void focusItem(Item it) {
        int i = items.indexOf(it);
        if (i >= 0) focus = i;
        repaintSelf();
    }

    private void fixFocus() {
        if (focus >= 0 && focus < items.size() && ((Item) items.elementAt(focus)).j4iFocusable()) return;
        focus = -1;
        for (int i = 0; i < items.size(); i++) {
            if (((Item) items.elementAt(i)).j4iFocusable()) {
                focus = i;
                return;
            }
        }
    }

    Item focused() { return focus >= 0 && focus < items.size() ? (Item) items.elementAt(focus) : null; }

    Command[] sortedCommands() {
        Command[] base = super.sortedCommands();
        Item f = focused();
        if (f == null || f.cmds.isEmpty()) return base;
        Command[] r = new Command[base.length + f.cmds.size()];
        for (int i = 0; i < f.cmds.size(); i++) r[i] = (Command) f.cmds.elementAt(i);
        System.arraycopy(base, 0, r, f.cmds.size(), base.length);
        return r;
    }

    /** Item commands go to the item's listener. */
    boolean fireItemCommand(Command c) {
        Item f = focused();
        if (f != null && f.cmds.contains(c)) {
            if (f.icl != null) f.icl.commandAction(c, f);
            return true;
        }
        return false;
    }

    int contentHeight(int w) {
        int h = 2;
        for (int i = 0; i < items.size(); i++) h += ((Item) items.elementAt(i)).j4iHeight(w - 8) + 4;
        return h;
    }

    private int itemTop(int idx, int w) {
        int y = 2;
        for (int i = 0; i < idx; i++) y += ((Item) items.elementAt(i)).j4iHeight(w - 8) + 4;
        return y;
    }

    void paintContent(Graphics g, int w, int h) {
        int y = 2 - scroll;
        for (int i = 0; i < items.size(); i++) {
            Item it = (Item) items.elementAt(i);
            int ih = it.j4iHeight(w - 8);
            if (y + ih >= 0 && y < h) {
                try {
                    it.j4iPaint(g, 4, y, w - 8, i == focus);
                } catch (Throwable t) {
                    j4i.MidletRunner.report("item paint", t);
                }
            }
            y += ih + 4;
        }
    }

    private void moveFocus(int dir) {
        int n = items.size();
        for (int i = focus + dir; i >= 0 && i < n; i += dir) {
            if (((Item) items.elementAt(i)).j4iFocusable()) {
                focus = i;
                int top = itemTop(i, Display.W);
                ensureVisible(top, top + ((Item) items.elementAt(i)).j4iHeight(Display.W - 8) + 4);
                repaintSelf();
                return;
            }
        }
        scrollBy(dir * Font.getDefaultFont().getHeight());
    }

    void j4iKey(int type, int code) {
        if (type == Display.EV_KEY_RELEASED) return;
        Item f = focused();
        int ga = gameAction(code);
        if (ga == Canvas.UP || ga == Canvas.DOWN) {
            if (f != null && f.j4iMoveInside(ga == Canvas.UP ? -1 : 1)) {
                repaintSelf();
                return;
            }
            if (f == null) scrollBy((ga == Canvas.UP ? -1 : 1) * Font.getDefaultFont().getHeight());
            else moveFocus(ga == Canvas.UP ? -1 : 1);
            return;
        }
        if (f != null && f.j4iKey(code)) repaintSelf();
    }

    void j4iPointer(int type, int x, int y) {
        if (type != Display.EV_POINTER_RELEASED) return;
        int cy = y - headerHeight() + scroll;
        int yy = 2;
        for (int i = 0; i < items.size(); i++) {
            Item it = (Item) items.elementAt(i);
            int ih = it.j4iHeight(Display.W - 8);
            if (cy >= yy && cy < yy + ih + 4) {
                if (it.j4iFocusable()) {
                    focus = i;
                    it.j4iTap(x - 4, cy - yy, Display.W - 8);
                }
                repaintSelf();
                return;
            }
            yy += ih + 4;
        }
    }

    void j4iShow() {
        for (int i = 0; i < items.size(); i++) ((Item) items.elementAt(i)).j4iShow();
    }

    void j4iHide() {
        for (int i = 0; i < items.size(); i++) ((Item) items.elementAt(i)).j4iHide();
    }
}
