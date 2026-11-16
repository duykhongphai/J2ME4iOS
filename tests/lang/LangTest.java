import java.io.*;
import java.util.*;

/**
 * VM semantics test. Compiled against j4i-runtime.jar, run on both a real JVM
 * and J2ME4iOS; the two outputs must be identical.
 */
public class LangTest {
    static int sInit = init("static");
    static final String CONST = "const";
    static int counter;

    static int init(String s) {
        p("clinit " + s);
        return 42;
    }

    static void p(String s) { System.out.println(s); }

    interface Shape {
        int area();
    }

    static abstract class Base implements Shape {
        String name() { return "base"; }

        public String toString() { return name() + ":" + area(); }
    }

    static class Rect extends Base {
        int w, h;

        Rect(int w, int h) {
            this.w = w;
            this.h = h;
        }

        public int area() { return w * h; }

        String name() { return "rect"; }
    }

    static class Square extends Rect {
        Square(int s) { super(s, s); }

        String name() { return "square(" + super.name() + ")"; }
    }

    static class Lazy {
        static { p("Lazy init"); }
        static int v = 7;
    }

    static class MyEx extends Exception {
        int code;

        MyEx(String m, int c) {
            super(m);
            code = c;
        }
    }

    static int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }

    static int thrower(int i) throws MyEx {
        if (i > 2) throw new MyEx("too big " + i, i);
        return i * 10;
    }

    static int finallyTest(int i) {
        try {
            if (i == 0) return 1;
            if (i == 1) throw new RuntimeException("x");
            return 2;
        } catch (RuntimeException e) {
            return 3;
        } finally {
            counter++;
        }
    }

    static String sw(int i) {
        switch (i) {
        case 1: return "one";
        case 2: return "two";
        case 3: return "three";
        case 10: return "ten";
        default: return "other";
        }
    }

    static String sw2(int i) {
        switch (i) {
        case -100: return "m100";
        case 5: return "five";
        case 1000: return "k";
        case 123456: return "big";
        default: return "dflt";
        }
    }

    public static void main(String[] args) throws Exception {
        p("start " + sInit + " " + CONST);

        // ints
        int a = 7, b = -3;
        p("int " + (a + b) + " " + (a - b) + " " + (a * b) + " " + (a / b) + " " + (a % b) + " " + (b / a) + " " + (b % a));
        p("shift " + (a << 3) + " " + (b >> 1) + " " + (b >>> 28) + " " + (1 << 33) + " " + (-1 >>> 31));
        p("minmax " + (Integer.MIN_VALUE / -1) + " " + (Integer.MIN_VALUE % -1) + " " + (Integer.MAX_VALUE + 1));
        p("bits " + (a & b) + " " + (a | b) + " " + (a ^ b) + " " + (~a));
        try {
            p("div0 " + (a / (b + 3)));
        } catch (ArithmeticException e) {
            p("caught " + e.getMessage());
        }
        // longs
        long x = 123456789012345L, y = -987654321L;
        p("long " + (x + y) + " " + (x - y) + " " + (x * y) + " " + (x / y) + " " + (x % y));
        p("lshift " + (x << 5) + " " + (y >> 3) + " " + (y >>> 40) + " " + (1L << 65) + " " + Long.MIN_VALUE / -1);
        p("lcmp " + (x > y) + " " + (x < y) + " " + (x == x));
        // floats & doubles
        float f = 1.5f, g = 0.1f;
        double d = 3.14159, e2 = 1e-5;
        p("float " + (f + g) + " " + (f * g) + " " + (f / g) + " " + (f % g) + " " + (-f));
        p("double " + (d + e2) + " " + (d * e2) + " " + (d / 7) + " " + (d % 1) + " " + (1.0 / 3));
        p("special " + (0.0 / 0.0) + " " + (1.0 / 0.0) + " " + (-1.0f / 0.0f) + " " + (float) 1e10 + " " + 1e-10 + " " + 100.0 + " " + 1234567.0 + " " + 12345678.0);
        p("conv " + (int) 3.99 + " " + (int) -3.99 + " " + (int) 1e20 + " " + (long) -1e30 + " " + (int) Double.NaN + " " + (char) 65 + " " + (byte) 200 + " " + (short) 70000);
        p("cmp " + (f > g) + " " + (Double.NaN < 1) + " " + (Double.NaN > 1) + " " + (Float.NaN == Float.NaN));
        p("math " + Math.sqrt(2) + " " + Math.sin(1) + " " + Math.floor(-2.5) + " " + Math.ceil(-2.5) + " " + Math.abs(-7) + " " + Math.max(3L, 9L));
        p("parse " + Integer.parseInt("-1234") + " " + Integer.parseInt("ff", 16) + " " + Long.parseLong("9876543210") + " " + Double.parseDouble("2.5e3") + " " + Float.parseFloat("0.25"));
        p("tostr " + Integer.toHexString(-1) + " " + Integer.toBinaryString(10) + " " + Integer.toString(255, 36) + " " + Long.toString(Long.MIN_VALUE));
        try {
            Integer.parseInt("12x");
        } catch (NumberFormatException ex) {
            p("nfe ok");
        }

        // strings
        String s = "Hello, World";
        p("str " + s.length() + " " + s.charAt(4) + " " + s.indexOf('o') + " " + s.lastIndexOf('o') + " " + s.indexOf("World") + " " + s.substring(7) + " " + s.toUpperCase() + " " + s.toLowerCase());
        p("str2 " + s.replace('l', 'L') + " [" + "  trim me ".trim() + "] " + s.startsWith("Hell") + " " + s.endsWith("ld") + " " + "abc".compareTo("abd") + " " + "b".equals("b") + " " + "AbC".equalsIgnoreCase("aBc"));
        p("hash " + "hello".hashCode() + " " + "".hashCode() + " " + s.concat("!"));
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 10; i++) sb.append(i).append(',');
        sb.insert(0, "[").append(']').deleteCharAt(sb.length() - 2).reverse();
        p("sb " + sb + " " + sb.length());
        p("intern " + (new String("abc").intern() == "abc"));
        char[] cs = {'x', 'y', 'z'};
        p("chars " + new String(cs) + " " + String.valueOf(cs, 1, 2) + " " + Character.isDigit('5') + " " + Character.toUpperCase('q'));
        byte[] utf = "héllo ✓".getBytes("UTF-8");
        p("utf8 " + utf.length + " " + new String(utf, "UTF-8"));

        // arrays
        int[] arr = new int[10];
        for (int i = 0; i < arr.length; i++) arr[i] = i * i;
        int[] copy = (int[]) arr.clone();
        System.arraycopy(arr, 0, arr, 2, 5);
        p("arr " + arr[2] + " " + arr[6] + " " + copy[6] + " " + arr.length);
        int[][] m = new int[3][4];
        m[1][2] = 5;
        long[][][] m3 = new long[2][3][4];
        m3[1][2][3] = 99L;
        p("multi " + m.length + " " + m[0].length + " " + m[1][2] + " " + m3[1][2][3]);
        String[] strs = {"b", "a", "c"};
        Object[] objs = strs;
        try {
            objs[0] = new Integer(1);
        } catch (ArrayStoreException ex) {
            p("ase ok");
        }
        try {
            arr[10] = 1;
        } catch (ArrayIndexOutOfBoundsException ex) {
            p("aioobe " + ex.getMessage());
        }
        try {
            int[] neg = new int[-1];
        } catch (NegativeArraySizeException ex) {
            p("nase ok");
        }
        boolean[] bools = new boolean[3];
        bools[1] = true;
        char[] chs = new char[2];
        chs[0] = 'A';
        short[] shs = {-5, 300};
        byte[] bs = {-1, 127};
        double[] ds = {1.5, -2.25};
        p("prim " + bools[1] + bools[0] + chs[0] + shs[0] + shs[1] + bs[0] + bs[1] + ds[0] + ds[1]);

        // objects & dispatch
        Shape[] shapes = {new Rect(2, 3), new Square(4)};
        for (int i = 0; i < shapes.length; i++) p("shape " + shapes[i] + " " + (shapes[i] instanceof Square) + " " + (shapes[i] instanceof Base));
        Object o = shapes[1];
        try {
            String bad = (String) o;
        } catch (ClassCastException ex) {
            p("cce ok");
        }
        p("lazy before");
        p("lazy " + Lazy.v);
        p("class " + shapes[1].getClass().getName() + " " + new int[0].getClass().getName() + " " + "x".getClass().getName());

        // exceptions
        for (int i = 0; i < 5; i++) {
            try {
                p("thrower " + thrower(i));
            } catch (MyEx ex) {
                p("myex " + ex.getMessage() + " " + ex.code);
            }
        }
        p("finally " + finallyTest(0) + finallyTest(1) + finallyTest(2) + " " + counter);
        try {
            Object nul = null;
            nul.toString();
        } catch (NullPointerException ex) {
            p("npe ok");
        }
        try {
            try {
                throw new IllegalStateException("inner");
            } finally {
                p("inner finally");
            }
        } catch (RuntimeException ex) {
            p("outer " + ex);
        }

        // switch
        p("switch " + sw(1) + sw(3) + sw(10) + sw(4) + " " + sw2(-100) + sw2(5) + sw2(1000) + sw2(123456) + sw2(0));

        // collections
        Vector v = new Vector();
        for (int i = 0; i < 20; i++) v.addElement(new Integer(i));
        v.removeElementAt(3);
        v.insertElementAt("x", 0);
        p("vector " + v.size() + " " + v.elementAt(0) + " " + v.elementAt(4) + " " + v.indexOf(new Integer(10)) + " " + v.contains("x"));
        Hashtable h = new Hashtable();
        for (int i = 0; i < 50; i++) h.put("k" + i, new Integer(i * 2));
        h.remove("k5");
        p("hashtable " + h.size() + " " + h.get("k7") + " " + h.get("k5") + " " + h.containsKey("k49"));
        int sum = 0;
        for (Enumeration en = h.elements(); en.hasMoreElements();) sum += ((Integer) en.nextElement()).intValue();
        p("hsum " + sum);
        Stack st = new Stack();
        st.push("a");
        st.push("b");
        p("stack " + st.pop() + st.peek() + st.size());
        Random r = new Random(12345);
        p("random " + r.nextInt() + " " + r.nextInt(100) + " " + r.nextLong() + " " + r.nextDouble());

        // io
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        dout.writeInt(0x12345678);
        dout.writeUTF("UTF ✓ test");
        dout.writeLong(-2L);
        dout.writeDouble(2.5);
        dout.writeBoolean(true);
        byte[] data = bout.toByteArray();
        DataInputStream din = new DataInputStream(new ByteArrayInputStream(data));
        p("io " + data.length + " " + Integer.toHexString(din.readInt()) + " " + din.readUTF() + " " + din.readLong() + " " + din.readDouble() + " " + din.readBoolean());
        try {
            din.readByte();
        } catch (EOFException ex) {
            p("eof ok");
        }

        // threads
        final int[] shared = new int[1];
        final Object lock = new Object();
        Thread[] ts = new Thread[4];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread() {
                public void run() {
                    for (int k = 0; k < 10000; k++) {
                        synchronized (lock) {
                            shared[0]++;
                        }
                    }
                }
            };
            ts[i].start();
        }
        for (int i = 0; i < ts.length; i++) ts[i].join();
        p("threads " + shared[0]);

        final Vector q = new Vector();
        Thread producer = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < 5; i++) {
                    synchronized (q) {
                        q.addElement(new Integer(i));
                        q.notifyAll();
                    }
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException ex) {
                    }
                }
            }
        });
        producer.start();
        int got = 0, total = 0;
        while (got < 5) {
            synchronized (q) {
                while (q.isEmpty()) q.wait();
                total += ((Integer) q.elementAt(0)).intValue();
                q.removeElementAt(0);
                got++;
            }
        }
        p("waitnotify " + total);
        long t0 = System.currentTimeMillis();
        Thread.sleep(50);
        long dt = System.currentTimeMillis() - t0;
        p("sleep " + (dt >= 45 && dt < 500));

        // recursion & perf
        p("fib " + fib(25));
        long big = 0;
        for (int i = 0; i < 3000000; i++) big += i % 7;
        p("loop " + big);

        // gc stress
        Vector keep = new Vector();
        for (int i = 0; i < 200000; i++) {
            byte[] junk = new byte[100];
            junk[0] = (byte) i;
            if (i % 1000 == 0) keep.addElement(junk);
        }
        p("gc " + keep.size() + " " + ((byte[]) keep.elementAt(5))[0]);
        String acc = "";
        for (int i = 0; i < 300; i++) acc += i;
        p("concat " + acc.length());
        p("done");
    }
}
