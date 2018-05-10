package com.samourai.whirlpool.server.beans;

public class RegisteredInput {
    private String username;
    private TxOutPoint input;
    private byte[] pubkey;
    private String paymentCode;
    private boolean liquidity;

    public RegisteredInput(String username, TxOutPoint input, byte[] pubkey, String paymentCode, boolean liquidity) {
        this.username = username;
        this.input = input;
        this.pubkey = pubkey;
        this.paymentCode = paymentCode;
        this.liquidity = liquidity;
    }

    public String getUsername() {
        return username;
    }

    public TxOutPoint getInput() {
        return input;
    }

    public byte[] getPubkey() {
        return pubkey;
    }

    public String getPaymentCode() {
        return paymentCode;
    }

    public boolean isLiquidity() {
        return liquidity;
    }
}
