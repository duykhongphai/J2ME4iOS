package javax.bluetooth;

public class UUID {
    private final String hex;

    public UUID(long uuidValue) { this(Long.toString(uuidValue, 16), true); }

    public UUID(String uuidValue, boolean shortUUID) {
        if (uuidValue == null) throw new NullPointerException();
        hex = uuidValue.toUpperCase();
    }

    public String toString() { return hex; }

    public boolean equals(Object o) { return o instanceof UUID && ((UUID) o).hex.equals(hex); }

    public int hashCode() { return hex.hashCode(); }
}
