package java.lang;

import java.io.InputStream;

public final class Class {
    private long vm;
    private String name;

    private Class() {}

    public static native Class forName(String className) throws ClassNotFoundException;

    public native Object newInstance() throws InstantiationException, IllegalAccessException;

    public native boolean isInstance(Object obj);

    public native boolean isAssignableFrom(Class cls);

    public native boolean isInterface();

    public native boolean isArray();

    private native String getName0();

    public String getName() {
        if (name == null) name = getName0();
        return name;
    }

    public String toString() { return (isInterface() ? "interface " : "class ") + getName(); }

    public InputStream getResourceAsStream(String res) {
        if (res == null) throw new NullPointerException();
        String path;
        if (res.startsWith("/")) {
            path = res.substring(1);
        } else {
            String n = getName();
            int i = n.lastIndexOf('.');
            path = i < 0 ? res : n.substring(0, i).replace('.', '/') + "/" + res;
        }
        byte[] data = j4i.Sys.getResource(path);
        if (data == null) return null;
        return new java.io.ByteArrayInputStream(data);
    }
}
