package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;

public class RegisteredInput {
  private String username;
  private TxOutPoint outPoint;
  private boolean liquidity;

  public RegisteredInput(String username, boolean liquidity, TxOutPoint outPoint) {
    this.username = username;
    this.liquidity = liquidity;
    this.outPoint = outPoint;
  }

  public String getUsername() {
    return username;
  }

  public boolean isLiquidity() {
    return liquidity;
  }

  public TxOutPoint getOutPoint() {
    return outPoint;
  }
}
