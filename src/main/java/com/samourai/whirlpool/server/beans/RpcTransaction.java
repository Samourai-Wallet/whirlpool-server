package com.samourai.whirlpool.server.beans;

import java.util.ArrayList;
import java.util.List;

public class RpcTransaction {
    private String txid;
    private int confirmations;
    private List<RpcIn> ins;
    private List<RpcOut> outs;

    public RpcTransaction() {

    }

    public RpcTransaction(String txid, int nbConfirmations) {
        this.txid = txid;
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

    public String getTxid() {
        return txid;
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
