package com.samourai.whirlpool.server.beans.rpc;

/**
 * RpcOut with associated tx.
 */
public class RpcOutWithTx {
    private RpcOut rpcOut;
    private RpcTransaction tx;

    public RpcOutWithTx() {

    }

    public RpcOutWithTx(RpcOut rpcOut, RpcTransaction tx) {
        if (!rpcOut.getHash().equals(tx.getTxid())) {
            throw new RuntimeException("bug: instanciating RpcOutWithTx with rpcOut.hash != tx.hash");
        }
        this.rpcOut = rpcOut;
        this.tx = tx;
    }

    public RpcOut getRpcOut() {
        return rpcOut;
    }

    public RpcTransaction getTx() {
        return tx;
    }
}
