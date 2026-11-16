/** System property and font size overrides (run with --prop/--font, J2ME4iOS only). */
public class PropMain {
    public static void main(String[] args) {
        PropTest.check("platform override", "SonyEricssonK800i".equals(System.getProperty("microedition.platform")));
        PropTest.check("custom property", "42".equals(System.getProperty("my.prop")));
        PropTest.check("default property", "CLDC-1.1".equals(System.getProperty("microedition.configuration")));
        int large = javax.microedition.lcdui.Font.getFont(0, 0, javax.microedition.lcdui.Font.SIZE_LARGE).getHeight();
        int small = javax.microedition.lcdui.Font.getFont(0, 0, javax.microedition.lcdui.Font.SIZE_SMALL).getHeight();
        PropTest.check("font size override " + small + "/" + large, large > small + 6);
        System.out.println(PropTest.failures == 0 ? "ALL OK" : PropTest.failures + " FAILURES");
    }
}
