package com.samourai.whirlpool.server.beans;

public class RegisteredInput {
    private String username;
    private TxOutPoint input;
    private byte[] pubkey;
    private String paymentCode;
    private boolean liquidity;
    private boolean offline;

    public RegisteredInput(String username, TxOutPoint input, byte[] pubkey, String paymentCode, boolean liquidity) {
        this.username = username;
        this.input = input;
        this.pubkey = pubkey;
        this.paymentCode = paymentCode;
        this.liquidity = liquidity;
        this.offline = false;
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

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public boolean isOffline() {
        return offline;
    }
}
