package com.samourai.whirlpool.server.beans;

public class RegisteredInput {
  private String username;
  private TxOutPoint input;
  private byte[] pubkey;
  private boolean liquidity;

  public RegisteredInput(String username, byte[] pubkey, boolean liquidity, TxOutPoint input) {
    this.username = username;
    this.pubkey = pubkey;
    this.liquidity = liquidity;
    this.input = input;
  }

  public String getUsername() {
    return username;
  }

  public byte[] getPubkey() {
    return pubkey;
  }

  public boolean isLiquidity() {
    return liquidity;
  }

  public TxOutPoint getInput() {
    return input;
  }
}
