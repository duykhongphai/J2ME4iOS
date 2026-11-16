package com.samsung.util;

/** Samsung short message container. */
public class SM {
    private String dest, callback, data;

    public SM() {}

    public SM(String dest, String callback, String textMessage) {
        this.dest = dest;
        this.callback = callback;
        this.data = textMessage;
    }

    public String getCallbackAddress() { return callback; }

    public String getData() { return data; }

    public String getDestAddress() { return dest; }

    public void setCallbackAddress(String address) { callback = address; }

    public void setData(String textMessage) { data = textMessage; }

    public void setDestAddress(String address) { dest = address; }
}
