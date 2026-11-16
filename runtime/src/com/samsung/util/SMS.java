package com.samsung.util;

import java.io.IOException;

/** Samsung SMS sending. Not available on this platform. */
public class SMS {
    private SMS() {}

    public static boolean isSupported() { return false; }

    public static void send(SM sm) throws IOException {
        if (sm == null) throw new NullPointerException();
        throw new IOException("SMS not supported");
    }
}
