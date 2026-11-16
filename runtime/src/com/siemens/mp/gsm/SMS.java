package com.siemens.mp.gsm;

import java.io.IOException;
import com.siemens.mp.NotAllowedException;

/** Siemens SMS sending. Not available on this platform. */
public class SMS {
    private SMS() {}

    public static int send(String number, String data) throws IOException, NotAllowedException {
        throw new NotAllowedException("SMS not supported");
    }

    public static int send(String number, byte[] data) throws IOException, NotAllowedException {
        throw new NotAllowedException("SMS not supported");
    }
}
