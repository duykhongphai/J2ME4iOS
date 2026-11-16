package com.siemens.mp.gsm;

import com.siemens.mp.NotAllowedException;

/** Siemens phone book access. No entries are exposed. */
public class PhoneBook {
    private PhoneBook() {}

    /** Own phone numbers (MSISDN). */
    public static String[] getMDN() throws NotAllowedException { return new String[0]; }
}
