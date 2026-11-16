package javax.microedition.rms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Hashtable;
import java.util.Vector;

/** RecordStore persisted as one file per store in the host data directory. */
public class RecordStore {
    public static final int AUTHMODE_PRIVATE = 0;
    public static final int AUTHMODE_ANY = 1;

    private static final int MAX_SIZE = 4 * 1024 * 1024;
    private static final Hashtable open = new Hashtable();

    private final String name;
    private int openCount;
    final Vector ids = new Vector();
    private final Hashtable data = new Hashtable();
    private int nextId = 1;
    int version;
    private long lastModified;
    private final Vector listeners = new Vector();

    private RecordStore(String name) { this.name = name; }

    private static String file(String name) { return "rms_" + name + ".rms"; }

    public static RecordStore openRecordStore(String recordStoreName, boolean createIfNecessary)
        throws RecordStoreException, RecordStoreFullException, RecordStoreNotFoundException {
        if (recordStoreName == null) throw new NullPointerException();
        if (recordStoreName.length() < 1 || recordStoreName.length() > 32) throw new IllegalArgumentException();
        synchronized (open) {
            RecordStore rs = (RecordStore) open.get(recordStoreName);
            if (rs == null) {
                byte[] b = j4i.Sys.readFile(file(recordStoreName));
                rs = new RecordStore(recordStoreName);
                if (b == null) {
                    if (!createIfNecessary) throw new RecordStoreNotFoundException(recordStoreName);
                    rs.lastModified = System.currentTimeMillis();
                    rs.save();
                } else {
                    rs.load(b);
                }
                open.put(recordStoreName, rs);
            }
            rs.openCount++;
            return rs;
        }
    }

    public static RecordStore openRecordStore(String recordStoreName, boolean createIfNecessary, int authmode, boolean writable)
        throws RecordStoreException, RecordStoreFullException, RecordStoreNotFoundException {
        return openRecordStore(recordStoreName, createIfNecessary);
    }

    public static RecordStore openRecordStore(String recordStoreName, String vendorName, String suiteName)
        throws RecordStoreException, RecordStoreNotFoundException {
        return openRecordStore(recordStoreName, false);
    }

    public void setMode(int authmode, boolean writable) throws RecordStoreException { checkOpen(); }

    public void closeRecordStore() throws RecordStoreNotOpenException, RecordStoreException {
        synchronized (open) {
            checkOpen();
            if (--openCount == 0) {
                open.remove(name);
                listeners.removeAllElements();
            }
        }
    }

    public static void deleteRecordStore(String recordStoreName) throws RecordStoreException, RecordStoreNotFoundException {
        synchronized (open) {
            RecordStore rs = (RecordStore) open.get(recordStoreName);
            if (rs != null && rs.openCount > 0) throw new RecordStoreException("record store is open");
            if (j4i.Sys.readFile(file(recordStoreName)) == null) throw new RecordStoreNotFoundException(recordStoreName);
            j4i.Sys.deleteFile(file(recordStoreName));
        }
    }

    public static String[] listRecordStores() {
        String[] files = j4i.Sys.listFiles();
        if (files == null) return null;
        Vector v = new Vector();
        for (int i = 0; i < files.length; i++) {
            String f = files[i];
            if (f.startsWith("rms_") && f.endsWith(".rms")) v.addElement(f.substring(4, f.length() - 4));
        }
        if (v.isEmpty()) return null;
        String[] r = new String[v.size()];
        v.copyInto(r);
        return r;
    }

    private void checkOpen() throws RecordStoreNotOpenException {
        if (openCount <= 0) throw new RecordStoreNotOpenException(name);
    }

    private void load(byte[] b) throws RecordStoreException {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
            if (in.readInt() != 0x4A345253) throw new RecordStoreException("corrupt record store");
            version = in.readInt();
            nextId = in.readInt();
            lastModified = in.readLong();
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                int id = in.readInt();
                int len = in.readInt();
                byte[] r = new byte[len];
                in.readFully(r);
                Integer key = new Integer(id);
                ids.addElement(key);
                data.put(key, r);
            }
        } catch (java.io.IOException e) {
            throw new RecordStoreException("corrupt record store");
        }
    }

    private void save() throws RecordStoreException {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream(256 + getSizeRaw());
            DataOutputStream out = new DataOutputStream(bo);
            out.writeInt(0x4A345253);
            out.writeInt(version);
            out.writeInt(nextId);
            out.writeLong(lastModified);
            out.writeInt(ids.size());
            for (int i = 0; i < ids.size(); i++) {
                Integer key = (Integer) ids.elementAt(i);
                byte[] r = (byte[]) data.get(key);
                out.writeInt(key.intValue());
                out.writeInt(r.length);
                out.write(r, 0, r.length);
            }
            if (!j4i.Sys.writeFile(file(name), bo.toByteArray())) throw new RecordStoreException("write failed");
        } catch (java.io.IOException e) {
            throw new RecordStoreException(e.toString());
        }
    }

    private int getSizeRaw() {
        int s = 0;
        for (int i = 0; i < ids.size(); i++) s += ((byte[]) data.get(ids.elementAt(i))).length + 8;
        return s;
    }

    private void changed() throws RecordStoreException {
        version++;
        lastModified = System.currentTimeMillis();
        save();
    }

    public String getName() throws RecordStoreNotOpenException {
        checkOpen();
        return name;
    }

    public int getVersion() throws RecordStoreNotOpenException {
        checkOpen();
        return version;
    }

    public int getNumRecords() throws RecordStoreNotOpenException {
        checkOpen();
        return ids.size();
    }

    public int getSize() throws RecordStoreNotOpenException {
        checkOpen();
        return getSizeRaw() + 24;
    }

    public int getSizeAvailable() throws RecordStoreNotOpenException {
        checkOpen();
        return Math.max(0, MAX_SIZE - getSizeRaw());
    }

    public long getLastModified() throws RecordStoreNotOpenException {
        checkOpen();
        return lastModified;
    }

    public void addRecordListener(RecordListener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.addElement(listener);
    }

    public void removeRecordListener(RecordListener listener) { listeners.removeElement(listener); }

    public int getNextRecordID() throws RecordStoreNotOpenException, RecordStoreException {
        checkOpen();
        return nextId;
    }

    public synchronized int addRecord(byte[] d, int offset, int numBytes)
        throws RecordStoreNotOpenException, RecordStoreException, RecordStoreFullException {
        checkOpen();
        if (d == null && numBytes > 0) throw new NullPointerException();
        if (getSizeRaw() + numBytes > MAX_SIZE) throw new RecordStoreFullException();
        byte[] r = new byte[numBytes];
        if (numBytes > 0) System.arraycopy(d, offset, r, 0, numBytes);
        int id = nextId++;
        Integer key = new Integer(id);
        ids.addElement(key);
        data.put(key, r);
        changed();
        for (int i = 0; i < listeners.size(); i++) ((RecordListener) listeners.elementAt(i)).recordAdded(this, id);
        return id;
    }

    private byte[] rec(int id) throws InvalidRecordIDException {
        byte[] r = (byte[]) data.get(new Integer(id));
        if (r == null) throw new InvalidRecordIDException("record " + id);
        return r;
    }

    public synchronized void deleteRecord(int recordId) throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
        checkOpen();
        rec(recordId);
        Integer key = new Integer(recordId);
        data.remove(key);
        ids.removeElement(key);
        changed();
        for (int i = 0; i < listeners.size(); i++) ((RecordListener) listeners.elementAt(i)).recordDeleted(this, recordId);
    }

    public int getRecordSize(int recordId) throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
        checkOpen();
        return rec(recordId).length;
    }

    public int getRecord(int recordId, byte[] buffer, int offset)
        throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
        checkOpen();
        byte[] r = rec(recordId);
        System.arraycopy(r, 0, buffer, offset, r.length);
        return r.length;
    }

    public byte[] getRecord(int recordId) throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
        checkOpen();
        byte[] r = rec(recordId);
        if (r.length == 0) return null;
        byte[] c = new byte[r.length];
        System.arraycopy(r, 0, c, 0, r.length);
        return c;
    }

    public synchronized void setRecord(int recordId, byte[] newData, int offset, int numBytes)
        throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException, RecordStoreFullException {
        checkOpen();
        rec(recordId);
        if (newData == null && numBytes > 0) throw new NullPointerException();
        byte[] r = new byte[numBytes];
        if (numBytes > 0) System.arraycopy(newData, offset, r, 0, numBytes);
        data.put(new Integer(recordId), r);
        changed();
        for (int i = 0; i < listeners.size(); i++) ((RecordListener) listeners.elementAt(i)).recordChanged(this, recordId);
    }

    public RecordEnumeration enumerateRecords(RecordFilter filter, RecordComparator comparator, boolean keepUpdated)
        throws RecordStoreNotOpenException {
        checkOpen();
        return new Enum(this, filter, comparator, keepUpdated);
    }

    byte[] peek(int id) {
        byte[] r = (byte[]) data.get(new Integer(id));
        return r;
    }

    private static final class Enum implements RecordEnumeration {
        private final RecordStore rs;
        private final RecordFilter filter;
        private final RecordComparator comparator;
        private boolean keep;
        private int[] list = new int[0];
        private int pos;
        private int builtVersion;
        private boolean destroyed;

        Enum(RecordStore rs, RecordFilter f, RecordComparator c, boolean keep) {
            this.rs = rs;
            this.filter = f;
            this.comparator = c;
            this.keep = keep;
            rebuild();
        }

        private void check() {
            if (destroyed) throw new IllegalStateException();
            if (keep && builtVersion != rs.version) {
                int cur = pos;
                rebuild();
                pos = Math.min(cur, list.length);
            }
        }

        public void rebuild() {
            Vector v = new Vector();
            for (int i = 0; i < rs.ids.size(); i++) {
                int id = ((Integer) rs.ids.elementAt(i)).intValue();
                byte[] d = rs.peek(id);
                if (filter == null || filter.matches(d)) v.addElement(new Integer(id));
            }
            int[] l = new int[v.size()];
            for (int i = 0; i < l.length; i++) l[i] = ((Integer) v.elementAt(i)).intValue();
            if (comparator != null) {
                for (int i = 1; i < l.length; i++) {
                    int key = l[i];
                    int j = i - 1;
                    while (j >= 0 && comparator.compare(rs.peek(l[j]), rs.peek(key)) == RecordComparator.FOLLOWS) {
                        l[j + 1] = l[j];
                        j--;
                    }
                    l[j + 1] = key;
                }
            }
            list = l;
            pos = 0;
            builtVersion = rs.version;
        }

        public int numRecords() {
            check();
            return list.length;
        }

        public byte[] nextRecord() throws InvalidRecordIDException, RecordStoreNotOpenException, RecordStoreException {
            return rs.getRecord(nextRecordId());
        }

        public int nextRecordId() throws InvalidRecordIDException {
            check();
            if (pos >= list.length) throw new InvalidRecordIDException();
            return list[pos++];
        }

        public byte[] previousRecord() throws InvalidRecordIDException, RecordStoreNotOpenException, RecordStoreException {
            return rs.getRecord(previousRecordId());
        }

        public int previousRecordId() throws InvalidRecordIDException {
            check();
            if (pos <= 0) {
                if (list.length == 0) throw new InvalidRecordIDException();
                pos = list.length;
            }
            return list[--pos];
        }

        public boolean hasNextElement() {
            check();
            return pos < list.length;
        }

        public boolean hasPreviousElement() {
            check();
            return pos > 0;
        }

        public void reset() {
            check();
            pos = 0;
        }

        public void keepUpdated(boolean k) {
            keep = k;
            if (k) rebuild();
        }

        public boolean isKeptUpdated() { return keep; }

        public void destroy() { destroyed = true; }
    }
}
