package com.siemens.mp.gsm;

import com.siemens.mp.NotAllowedException;

/** Siemens voice call. Not available on this platform. */
public class Call {
    private Call() {}

    public static void start(String number) throws NotAllowedException {
        if (number == null) throw new IllegalArgumentException();
        throw new NotAllowedException("Calls not supported");
    }
}
