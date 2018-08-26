package com.samourai.whirlpool.server.services.rpc;

import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

import java.util.Date;
import java.util.List;

public class NotImplementedMockRawTransactionImpl implements BitcoindRpcClient.RawTransaction {
    @Override
    public String hex() {
        throw new RuntimeException("mock not implemented");
    }

    @Override
    public String txId() {
        throw new RuntimeException("mock not implemented");
    }

    @Override
    public int version() {
        throw new RuntimeException("mock not implemented");
    }

    @Override
    public long lockTime() {
        throw new RuntimeException("mock not implemented");
    }

    @Override
    public long size() {
        throw new RuntimeException("mock not implemented");
    }

    @Override
    public long vsize() {
        throw new RuntimeException("mock not implemented");
    }

    @Override
    public String hash() {
        throw new RuntimeException("mock not implemented");
    }

    @Override
    public long height() {
        throw new RuntimeException("mock not implemented");
    }

    @Override
    public List<In> vIn() {
        throw new RuntimeException("mock not implemented");
    }

    @Override
    public List<Out> vOut() {
        throw new RuntimeException("mock not implemented");
    }

    @Override
    public String blockHash() {
        throw new RuntimeException("mock not implemented");
    }

    @Override
    public Integer confirmations() {
        throw new RuntimeException("mock not implemented");
    }

    @Override
    public Date time() {
        throw new RuntimeException("mock not implemented");
    }

    @Override
    public Date blocktime() {
        throw new RuntimeException("mock not implemented");
    }
}
