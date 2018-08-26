package com.samourai.whirlpool.server.services.rpc;

public class MockRawTransactionImpl extends NotImplementedMockRawTransactionImpl {
    private String txid;
    private String hex;
    private int confirmations;

    public MockRawTransactionImpl(String txid, String hex, int confirmations) {
        this.txid = txid;
        this.hex = hex;
        this.confirmations = confirmations;
    }

    @Override
    public String txId() {
        return txid;
    }

    @Override
    public String hex() {
        return hex;
    }

    @Override
    public Integer confirmations() {
        return confirmations;
    }
}
