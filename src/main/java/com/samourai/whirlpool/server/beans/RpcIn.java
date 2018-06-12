package com.samourai.whirlpool.server.beans;

public class RpcIn {
    private RpcOut fromOut;
    private RpcTransaction fromTx;

    public RpcIn() {

    }

    public RpcIn(RpcOut fromOut, RpcTransaction fromTx) {
        this.fromOut = fromOut;
        this.fromTx = fromTx;
    }

    public RpcOut getFromOut() {
        return fromOut;
    }

    public RpcTransaction getFromTx() {
        return fromTx;
    }
}
