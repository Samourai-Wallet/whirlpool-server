package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;

public class RegisteredInput {
  private String username;
  private TxOutPoint outPoint;
  private boolean liquidity;
  private String ip;

  public RegisteredInput(String username, boolean liquidity, TxOutPoint outPoint, String ip) {
    this.username = username;
    this.liquidity = liquidity;
    this.outPoint = outPoint;
    this.ip = ip;
  }

  public void changeUsername(String username) {
    this.username = username;
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

  public String getIp() {
    return ip;
  }
}
