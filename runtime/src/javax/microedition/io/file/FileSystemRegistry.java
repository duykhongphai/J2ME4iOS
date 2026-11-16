package javax.microedition.io.file;

import java.util.Enumeration;
import java.util.Vector;

public class FileSystemRegistry {
    private static final Vector listeners = new Vector();

    private FileSystemRegistry() {}

    public static boolean addFileSystemListener(FileSystemListener listener) {
        if (listener == null) throw new NullPointerException();
        if (!listeners.contains(listener)) listeners.addElement(listener);
        return true;
    }

    public static boolean removeFileSystemListener(FileSystemListener listener) {
        if (listener == null) throw new NullPointerException();
        return listeners.removeElement(listener);
    }

    public static Enumeration listRoots() {
        Vector v = new Vector();
        String[] roots = j4i.Sys.fsList("/");
        if (roots != null)
            for (int i = 0; i < roots.length; i++)
                if (roots[i].endsWith("/")) v.addElement(roots[i]);
        if (v.isEmpty()) v.addElement("root1/");
        return v.elements();
    }
}
