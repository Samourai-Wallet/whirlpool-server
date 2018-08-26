package com.samourai.whirlpool.server.beans.rpc;

public class RpcOut {
    private String hash;
    private long index;
    private long value;
    private byte[] scriptPubKey;
    private String toAddress;

    public RpcOut() {

    }

    public RpcOut(String hash, long index, long value, byte[] scriptPubKey, String toAddress) {
        this.hash = hash;
        this.index = index;
        this.value = value;
        this.scriptPubKey = scriptPubKey;
        this.toAddress = toAddress;
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
