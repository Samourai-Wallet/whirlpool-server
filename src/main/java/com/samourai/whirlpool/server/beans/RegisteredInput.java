package com.samourai.whirlpool.server.beans;

public class RegisteredInput {
    private String username;
    private TxOutPoint input;
    private byte[] pubkey;
    private byte[] blindedBordereau;
    private boolean liquidity;
    private boolean offline;

    public RegisteredInput(String username, TxOutPoint input, byte[] pubkey, byte[] blindedBordereau, boolean liquidity) {
        this.username = username;
        this.input = input;
        this.pubkey = pubkey;
        this.blindedBordereau = blindedBordereau;
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

    public byte[] getBlindedBordereau() {
        return blindedBordereau;
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
