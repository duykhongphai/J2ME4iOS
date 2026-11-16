package com.siemens.mp.io;

import com.siemens.mp.NotAllowedException;

/** Siemens SMS data connection ("SMS://+number:port"). Sending is not available here. */
public class Connection extends com.siemens.mp.misc.NativeMem {
    private final String connectTo;
    private ConnectionListener listener;

    public Connection(String connectTo) { this.connectTo = connectTo; }

    public ConnectionListener getListener() { return listener; }

    public void setListener(ConnectionListener listener) { this.listener = listener; }

    public void send(byte[] data) throws NotAllowedException {
        throw new NotAllowedException("SMS not supported: " + connectTo);
    }
}
