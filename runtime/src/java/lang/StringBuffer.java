package java.lang;

public final class StringBuffer {
    private char[] value;
    private int count;

    public StringBuffer() { value = new char[16]; }

    public StringBuffer(int length) {
        if (length < 0) throw new NegativeArraySizeException();
        value = new char[length];
    }

    public StringBuffer(String str) {
        value = new char[str.length() + 16];
        append(str);
    }

    public int length() { return count; }

    public int capacity() { return value.length; }

    private void expand(int min) {
        int n = (value.length + 1) * 2;
        if (n < min) n = min;
        char[] nv = new char[n];
        System.arraycopy(value, 0, nv, 0, count);
        value = nv;
    }

    public void ensureCapacity(int min) {
        if (min > value.length) expand(min);
    }

    public void setLength(int newLength) {
        if (newLength < 0) throw new StringIndexOutOfBoundsException(newLength);
        if (newLength > value.length) expand(newLength);
        for (int i = count; i < newLength; i++) value[i] = 0;
        count = newLength;
    }

    public char charAt(int index) {
        if (index < 0 || index >= count) throw new StringIndexOutOfBoundsException(index);
        return value[index];
    }

    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        if (srcBegin < 0 || srcEnd > count || srcBegin > srcEnd) throw new StringIndexOutOfBoundsException();
        System.arraycopy(value, srcBegin, dst, dstBegin, srcEnd - srcBegin);
    }

    public void setCharAt(int index, char ch) {
        if (index < 0 || index >= count) throw new StringIndexOutOfBoundsException(index);
        value[index] = ch;
    }

    public StringBuffer append(Object obj) { return append(String.valueOf(obj)); }

    public StringBuffer append(String str) {
        if (str == null) str = "null";
        int len = str.length();
        int nc = count + len;
        if (nc > value.length) expand(nc);
        str.getChars(0, len, value, count);
        count = nc;
        return this;
    }

    public StringBuffer append(StringBuffer sb) {
        if (sb == null) return append("null");
        return append(sb.toString());
    }

    public StringBuffer append(char[] str) { return append(str, 0, str.length); }

    public StringBuffer append(char[] str, int offset, int len) {
        int nc = count + len;
        if (nc > value.length) expand(nc);
        System.arraycopy(str, offset, value, count, len);
        count = nc;
        return this;
    }

    public StringBuffer append(boolean b) { return append(b ? "true" : "false"); }

    public StringBuffer append(char c) {
        if (count + 1 > value.length) expand(count + 1);
        value[count++] = c;
        return this;
    }

    public StringBuffer append(int i) { return append(Integer.toString(i)); }

    public StringBuffer append(long l) { return append(Long.toString(l)); }

    public StringBuffer append(float f) { return append(Float.toString(f)); }

    public StringBuffer append(double d) { return append(Double.toString(d)); }

    public StringBuffer delete(int start, int end) {
        if (end > count) end = count;
        if (start < 0 || start > end) throw new StringIndexOutOfBoundsException();
        int len = end - start;
        if (len > 0) {
            System.arraycopy(value, start + len, value, start, count - end);
            count -= len;
        }
        return this;
    }

    public StringBuffer deleteCharAt(int index) {
        if (index < 0 || index >= count) throw new StringIndexOutOfBoundsException();
        System.arraycopy(value, index + 1, value, index, count - index - 1);
        count--;
        return this;
    }

    public StringBuffer replace(int start, int end, String str) {
        if (end > count) end = count;
        delete(start, end);
        return insert(start, str);
    }

    public StringBuffer insert(int offset, Object obj) { return insert(offset, String.valueOf(obj)); }

    public StringBuffer insert(int offset, String str) {
        if (offset < 0 || offset > count) throw new StringIndexOutOfBoundsException();
        if (str == null) str = "null";
        int len = str.length();
        int nc = count + len;
        if (nc > value.length) expand(nc);
        System.arraycopy(value, offset, value, offset + len, count - offset);
        str.getChars(0, len, value, offset);
        count = nc;
        return this;
    }

    public StringBuffer insert(int offset, char[] str) { return insert(offset, new String(str)); }

    public StringBuffer insert(int offset, boolean b) { return insert(offset, b ? "true" : "false"); }

    public StringBuffer insert(int offset, char c) {
        if (offset < 0 || offset > count) throw new StringIndexOutOfBoundsException();
        if (count + 1 > value.length) expand(count + 1);
        System.arraycopy(value, offset, value, offset + 1, count - offset);
        value[offset] = c;
        count++;
        return this;
    }

    public StringBuffer insert(int offset, int i) { return insert(offset, Integer.toString(i)); }

    public StringBuffer insert(int offset, long l) { return insert(offset, Long.toString(l)); }

    public StringBuffer insert(int offset, float f) { return insert(offset, Float.toString(f)); }

    public StringBuffer insert(int offset, double d) { return insert(offset, Double.toString(d)); }

    public StringBuffer reverse() {
        int n = count - 1;
        for (int j = (n - 1) >> 1; j >= 0; --j) {
            char tmp = value[j];
            value[j] = value[n - j];
            value[n - j] = tmp;
        }
        return this;
    }

    public int indexOf(String str) { return toString().indexOf(str); }

    public int indexOf(String str, int from) { return toString().indexOf(str, from); }

    public String substring(int start) { return substring(start, count); }

    public String substring(int start, int end) {
        if (start < 0 || end > count || start > end) throw new StringIndexOutOfBoundsException();
        return new String(value, start, end - start);
    }

    public String toString() { return new String(value, 0, count); }
}
