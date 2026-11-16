package java.util;

public class Hashtable {
    private static class Entry {
        int hash;
        Object key;
        Object value;
        Entry next;
    }

    private Entry[] table;
    private int count;
    private int threshold;

    public Hashtable(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException();
        if (initialCapacity == 0) initialCapacity = 1;
        table = new Entry[initialCapacity];
        threshold = (int) ((initialCapacity * 3L) / 4);
    }

    public Hashtable() { this(11); }

    public int size() { return count; }

    public boolean isEmpty() { return count == 0; }

    public synchronized Enumeration keys() { return new Enumerator(true); }

    public synchronized Enumeration elements() { return new Enumerator(false); }

    public synchronized boolean contains(Object value) {
        if (value == null) throw new NullPointerException();
        for (int i = table.length; i-- > 0;)
            for (Entry e = table[i]; e != null; e = e.next)
                if (e.value.equals(value)) return true;
        return false;
    }

    public boolean containsValue(Object value) { return contains(value); }

    public synchronized boolean containsKey(Object key) {
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % table.length;
        for (Entry e = table[index]; e != null; e = e.next)
            if (e.hash == hash && e.key.equals(key)) return true;
        return false;
    }

    public synchronized Object get(Object key) {
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % table.length;
        for (Entry e = table[index]; e != null; e = e.next)
            if (e.hash == hash && e.key.equals(key)) return e.value;
        return null;
    }

    protected void rehash() {
        int oldCapacity = table.length;
        Entry[] oldMap = table;
        int newCapacity = oldCapacity * 2 + 1;
        Entry[] newMap = new Entry[newCapacity];
        threshold = (int) ((newCapacity * 3L) / 4);
        table = newMap;
        for (int i = oldCapacity; i-- > 0;) {
            for (Entry old = oldMap[i]; old != null;) {
                Entry e = old;
                old = old.next;
                int index = (e.hash & 0x7FFFFFFF) % newCapacity;
                e.next = newMap[index];
                newMap[index] = e;
            }
        }
    }

    public synchronized Object put(Object key, Object value) {
        if (value == null) throw new NullPointerException();
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % table.length;
        for (Entry e = table[index]; e != null; e = e.next) {
            if (e.hash == hash && e.key.equals(key)) {
                Object old = e.value;
                e.value = value;
                return old;
            }
        }
        if (count >= threshold) {
            rehash();
            index = (hash & 0x7FFFFFFF) % table.length;
        }
        Entry e = new Entry();
        e.hash = hash;
        e.key = key;
        e.value = value;
        e.next = table[index];
        table[index] = e;
        count++;
        return null;
    }

    public synchronized Object remove(Object key) {
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % table.length;
        for (Entry e = table[index], prev = null; e != null; prev = e, e = e.next) {
            if (e.hash == hash && e.key.equals(key)) {
                if (prev != null) prev.next = e.next;
                else table[index] = e.next;
                count--;
                return e.value;
            }
        }
        return null;
    }

    public synchronized void clear() {
        for (int i = table.length; --i >= 0;) table[i] = null;
        count = 0;
    }

    public synchronized String toString() {
        StringBuffer sb = new StringBuffer("{");
        boolean first = true;
        for (int i = 0; i < table.length; i++) {
            for (Entry e = table[i]; e != null; e = e.next) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(String.valueOf(e.key)).append('=').append(String.valueOf(e.value));
            }
        }
        return sb.append('}').toString();
    }

    private class Enumerator implements Enumeration {
        boolean keys;
        int index = table.length;
        Entry entry;

        Enumerator(boolean keys) { this.keys = keys; }

        public boolean hasMoreElements() {
            while (entry == null && index > 0) entry = table[--index];
            return entry != null;
        }

        public Object nextElement() {
            while (entry == null && index > 0) entry = table[--index];
            if (entry != null) {
                Entry e = entry;
                entry = e.next;
                return keys ? e.key : e.value;
            }
            throw new NoSuchElementException("Hashtable Enumerator");
        }
    }
}
