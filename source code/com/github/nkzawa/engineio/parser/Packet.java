package com.github.nkzawa.engineio.parser;


public class Packet<T> {

    static final public String OPEN = "open";
    static final public String CLOSE = "close";
    static final public String PING = "ping";
    static final public String PONG = "pong";
    static final public String UPGRADE = "upgrade";
    static final public String MESSAGE = "message";
    static final public String NOOP = "noop";
    static final public String ERROR = "error";

    public String type;
    public T data;


    public Packet(String type) {
        this(type, null);
    }

    public Packet(String type, T data) {
        this.type = type;
        this.data = data;
    }
    
    public String toString() {
    	String str = "not string";
    	int len = 0;
    	if (data instanceof String) {
    		str = (String) data;
    		len = str.length();
    	}
    	String msg = "packet: {type: " + type + ", data: " + str + "}";
    	if (msg.length() > 255) {
    		msg = msg.substring(0, 255) + "..." + ", data length=" + len;
    	}
    	return msg;
    }
}
