package com.samourai.whirlpool.server.to;

public abstract class AbstractTO {
    private long timestamp;

    public AbstractTO() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}
