package com.samourai.whirlpool.server.services.rpc;

public class RpcRawTransactionResponse {
    private String hex;
    private int confirmations;

    public RpcRawTransactionResponse(String hex, Integer confirmations) {
        this.hex = hex;
        this.confirmations = (confirmations != null ? confirmations : 0);
    }

    public String getHex() {
        return hex;
    }

    public int getConfirmations() {
        return confirmations;
    }
}
