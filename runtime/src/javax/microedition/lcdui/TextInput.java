package javax.microedition.lcdui;

import java.util.Hashtable;

/** Bridges TextField/TextBox editing to the host's native keyboard. */
final class TextInput {
    interface Target {
        void j4iSetText(String s);
    }

    private static final Hashtable pending = new Hashtable();

    private TextInput() {}

    static void request(Target target, String title, String text, int maxLen, int constraints) {
        int id = j4i.Sys.requestTextInput(title, text, maxLen, constraints);
        pending.put(new Integer(id), target);
    }

    static void complete(int id, boolean ok) {
        Target t = (Target) pending.remove(new Integer(id));
        String s = j4i.Sys.takeTextInput(id);
        if (t == null || !ok || s == null) return;
        t.j4iSetText(s);
    }

    static boolean valid(String s, int constraints) {
        int c = constraints & TextField.CONSTRAINT_MASK;
        if (s == null || s.length() == 0) return true;
        if (c == TextField.NUMERIC) {
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (!(ch >= '0' && ch <= '9') && !(i == 0 && ch == '-')) return false;
            }
        } else if (c == TextField.DECIMAL) {
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (!(ch >= '0' && ch <= '9') && ch != '.' && !(i == 0 && ch == '-')) return false;
            }
        }
        return true;
    }

    static String display(String s, int constraints) {
        if ((constraints & TextField.PASSWORD) == 0 || s == null) return s;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) sb.append('*');
        return sb.toString();
    }
}
