package com.samourai.whirlpool.server.beans;

import java.util.ArrayList;
import java.util.List;

public class RpcTransaction {
    private String hash;
    private int confirmations;
    private List<RpcIn> ins;
    private List<RpcOut> outs;

    public RpcTransaction() {

    }

    public RpcTransaction(String hash, int nbConfirmations) {
        this.hash = hash;
        this.confirmations = nbConfirmations;
        this.ins = new ArrayList<>();
        this.outs = new ArrayList<>();
    }

    public void addRpcIn(RpcIn rpcIn) {
        this.ins.add(rpcIn);
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

    public List<RpcIn> getIns() {
        return ins;
    }

    public List<RpcOut> getOuts() {
        return outs;
    }
}
