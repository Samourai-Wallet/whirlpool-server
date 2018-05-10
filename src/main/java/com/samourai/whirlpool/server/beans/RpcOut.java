package com.samourai.whirlpool.server.beans;

import java.util.List;

public class RpcOut {
    private long index;
    private long value;
    private byte[] scriptPubKey;
    private List<String> toAddresses;

    public RpcOut() {

    }

    public RpcOut(long index, long value, byte[] scriptPubKey, List<String> toAddresses) {
        this.index = index;
        this.value = value;
        this.scriptPubKey = scriptPubKey;
        this.toAddresses = toAddresses;
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

    public String getToAddressSingle() {
        if (toAddresses != null && toAddresses.size() == 1) {
            return toAddresses.get(0);
        }
        return null;
    }
}
