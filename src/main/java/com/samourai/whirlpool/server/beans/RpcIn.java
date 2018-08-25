package com.samourai.whirlpool.server.beans;

public class RpcIn {
    private String originHash;
    private long originIndex;

    public RpcIn() {

    }

    public RpcIn(String originHash, long originIndex) {
        this.originHash = originHash;
        this.originIndex = originIndex;
    }

    public String getOriginHash() {
        return originHash;
    }

    public long getOriginIndex() {
        return originIndex;
    }
}
