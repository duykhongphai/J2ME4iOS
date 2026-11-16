package com.siemens.mp.io;

/** Receives incoming SMS data for a Siemens Connection. */
public interface ConnectionListener {
    void receiveData(byte[] data);
}
