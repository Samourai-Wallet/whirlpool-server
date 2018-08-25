package com.samourai.whirlpool.server.beans;

import java.util.List;

public class RpcOut {
    private String hash;
    private long index;
    private long value;
    private byte[] scriptPubKey;
    private String toAddress;

    public RpcOut() {

    }

    public RpcOut(String hash, long index, long value, byte[] scriptPubKey, List<String> toAddresses) {
        this.hash = hash;
        this.index = index;
        this.value = value;
        this.scriptPubKey = scriptPubKey;
        this.toAddress = (toAddresses != null ? toAddresses.get(0) : null);
    }

    public String getHash() {
        return hash;
    }

    public long getIndex() {
        return index;
    }

    public byte[] getScriptPubKey() {
        return scriptPubKey;
    }

    public long getValue() {
        return value;
    }

    public String getToAddress() {
        return toAddress;
    }
}
