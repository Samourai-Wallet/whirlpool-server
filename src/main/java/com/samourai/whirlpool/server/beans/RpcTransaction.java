package com.samourai.whirlpool.server.beans;

import java.util.ArrayList;
import java.util.List;

public class RpcTransaction {
    private String hash;
    private int confirmations;
    private List<RpcOut> outs;

    public RpcTransaction() {

    }

    public RpcTransaction(String hash, int nbConfirmations) {
        this.hash = hash;
        this.confirmations = nbConfirmations;
        this.outs = new ArrayList<>();
    }

    public void addRpcOut(RpcOut rpcOut) {
        this.outs.add(rpcOut);
    }

    public String getHash() {
        return hash;
    }

    public int getConfirmations() {
        return confirmations;
    }

    public List<RpcOut> getOuts() {
        return outs;
    }
}
