package javax.microedition.lcdui;

/** Shared element storage and rendering for List and ChoiceGroup. */
final class ChoiceModel {
    final int type;
    String[] strs = new String[8];
    Image[] imgs = new Image[8];
    boolean[] sel = new boolean[8];
    Font[] fonts = new Font[8];
    int n;
    int focus;
    int fit;

    ChoiceModel(int type) { this.type = type; }

    int size() { return n; }

    private void check(int i) {
        if (i < 0 || i >= n) throw new IndexOutOfBoundsException("index " + i);
    }

    String getString(int i) {
        check(i);
        return strs[i];
    }

    Image getImage(int i) {
        check(i);
        return imgs[i];
    }

    private void grow() {
        if (n < strs.length) return;
        int c = strs.length * 2;
        String[] s = new String[c];
        Image[] im = new Image[c];
        boolean[] b = new boolean[c];
        Font[] f = new Font[c];
        System.arraycopy(strs, 0, s, 0, n);
        System.arraycopy(imgs, 0, im, 0, n);
        System.arraycopy(sel, 0, b, 0, n);
        System.arraycopy(fonts, 0, f, 0, n);
        strs = s;
        imgs = im;
        sel = b;
        fonts = f;
    }

    int append(String s, Image img) {
        insert(n, s, img);
        return n - 1;
    }

    void insert(int i, String s, Image img) {
        if (s == null) throw new NullPointerException();
        if (i < 0 || i > n) throw new IndexOutOfBoundsException();
        grow();
        System.arraycopy(strs, i, strs, i + 1, n - i);
        System.arraycopy(imgs, i, imgs, i + 1, n - i);
        System.arraycopy(sel, i, sel, i + 1, n - i);
        System.arraycopy(fonts, i, fonts, i + 1, n - i);
        strs[i] = s;
        imgs[i] = img;
        sel[i] = false;
        fonts[i] = null;
        n++;
        if (n == 1 && type != Choice.MULTIPLE) sel[0] = true;
    }

    void delete(int i) {
        check(i);
        boolean wasSel = sel[i];
        System.arraycopy(strs, i + 1, strs, i, n - i - 1);
        System.arraycopy(imgs, i + 1, imgs, i, n - i - 1);
        System.arraycopy(sel, i + 1, sel, i, n - i - 1);
        System.arraycopy(fonts, i + 1, fonts, i, n - i - 1);
        n--;
        strs[n] = null;
        imgs[n] = null;
        if (wasSel && type != Choice.MULTIPLE && n > 0) sel[Math.min(i, n - 1)] = true;
        if (focus >= n) focus = Math.max(0, n - 1);
    }

    void deleteAll() {
        for (int i = 0; i < n; i++) {
            strs[i] = null;
            imgs[i] = null;
            sel[i] = false;
        }
        n = 0;
        focus = 0;
    }

    void set(int i, String s, Image img) {
        check(i);
        if (s == null) throw new NullPointerException();
        strs[i] = s;
        imgs[i] = img;
    }

    boolean isSelected(int i) {
        check(i);
        return sel[i];
    }

    int getSelectedIndex() {
        if (type == Choice.MULTIPLE) return -1;
        for (int i = 0; i < n; i++) if (sel[i]) return i;
        return -1;
    }

    int getSelectedFlags(boolean[] a) {
        if (a.length < n) throw new IllegalArgumentException();
        int c = 0;
        for (int i = 0; i < a.length; i++) {
            a[i] = i < n && sel[i];
            if (a[i]) c++;
        }
        return c;
    }

    void setSelectedIndex(int i, boolean s) {
        check(i);
        if (type == Choice.MULTIPLE) sel[i] = s;
        else if (s) {
            for (int k = 0; k < n; k++) sel[k] = false;
            sel[i] = true;
        }
    }

    void setSelectedFlags(boolean[] a) {
        if (a.length < n) throw new IllegalArgumentException();
        if (type == Choice.MULTIPLE) {
            for (int i = 0; i < n; i++) sel[i] = a[i];
        } else {
            int first = -1;
            for (int i = 0; i < n; i++) if (a[i]) { first = i; break; }
            if (first < 0 && n > 0) first = 0;
            for (int i = 0; i < n; i++) sel[i] = i == first;
        }
    }

    void setFont(int i, Font f) {
        check(i);
        fonts[i] = f;
    }

    Font getFont(int i) {
        check(i);
        return fonts[i] != null ? fonts[i] : Font.getDefaultFont();
    }

    int rowHeight(int i) {
        int h = getFont(i).getHeight();
        if (imgs[i] != null && imgs[i].h > h) h = imgs[i].h;
        return h + 6;
    }

    int height(int w) {
        if (type == Choice.POPUP) {
            int s = getSelectedIndex();
            return s >= 0 ? rowHeight(s) : Font.getDefaultFont().getHeight() + 6;
        }
        int h = 0;
        for (int i = 0; i < n; i++) h += rowHeight(i);
        return h;
    }

    int[] rowBounds(int idx, int w) {
        int y = 0;
        for (int i = 0; i < n && i < idx; i++) y += rowHeight(i);
        return new int[] {y, idx < n ? y + rowHeight(idx) : y};
    }

    int indexAt(int y, int w) {
        if (y < 0) return -1;
        int yy = 0;
        for (int i = 0; i < n; i++) {
            int h = rowHeight(i);
            if (y < yy + h) return i;
            yy += h;
        }
        return -1;
    }

    boolean move(int dir, boolean wrap) {
        if (n == 0) return false;
        int nf = focus + dir;
        if (nf < 0 || nf >= n) {
            if (!wrap) return false;
            nf = (nf + n) % n;
        }
        focus = nf;
        return true;
    }

    void activate() {
        if (n == 0) return;
        if (type == Choice.MULTIPLE) sel[focus] = !sel[focus];
        else if (type == Choice.POPUP) {
            int s = getSelectedIndex();
            setSelectedIndex((s + 1) % n, true);
        } else setSelectedIndex(focus, true);
    }

    void paint(Graphics g, int x, int y, int w, boolean active) {
        if (type == Choice.POPUP) {
            int s = getSelectedIndex();
            int h = height(w);
            g.setColor(active ? Screen.HL_BG : Screen.BORDER);
            g.drawRect(x, y, w - 1, h - 1);
            g.setColor(Screen.FG);
            g.setFont(Font.getDefaultFont());
            if (s >= 0) g.drawString(strs[s], x + 4, y + 3, Graphics.LEFT | Graphics.TOP);
            g.fillTriangle(x + w - 14, y + h / 2 - 2, x + w - 6, y + h / 2 - 2, x + w - 10, y + h / 2 + 3);
            return;
        }
        int yy = y;
        for (int i = 0; i < n; i++) {
            int h = rowHeight(i);
            boolean foc = active && i == focus;
            if (foc) {
                g.setColor(Screen.HL_BG);
                g.fillRect(x, yy, w, h);
            }
            int tx = x + 4;
            int fg = foc ? Screen.HL_FG : Screen.FG;
            if (type == Choice.EXCLUSIVE || type == Choice.MULTIPLE) {
                int bs = Math.min(12, h - 6);
                int by = yy + (h - bs) / 2;
                g.setColor(fg);
                if (type == Choice.EXCLUSIVE) {
                    g.drawArc(tx, by, bs, bs, 0, 360);
                    if (sel[i]) g.fillArc(tx + 3, by + 3, bs - 5, bs - 5, 0, 360);
                } else {
                    g.drawRect(tx, by, bs, bs);
                    if (sel[i]) g.fillRect(tx + 3, by + 3, bs - 5, bs - 5);
                }
                tx += bs + 6;
            }
            if (imgs[i] != null) {
                g.drawImage(imgs[i], tx, yy + h / 2, Graphics.LEFT | Graphics.VCENTER);
                tx += imgs[i].w + 4;
            }
            Font f = getFont(i);
            g.setFont(f);
            g.setColor(fg);
            g.drawString(Screen.clipText(strs[i], f, x + w - tx - 4), tx, yy + (h - f.getHeight()) / 2, Graphics.LEFT | Graphics.TOP);
            yy += h;
        }
    }
}
