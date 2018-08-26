package com.samourai.whirlpool.server.services.rpc;

public class RpcRawTransactionResponse {
    private String hex;
    private int confirmations;

    public RpcRawTransactionResponse(String hex, int confirmations) {
        this.hex = hex;
        this.confirmations = confirmations;
    }

    public String getHex() {
        return hex;
    }

    public int getConfirmations() {
        return confirmations;
    }
}
