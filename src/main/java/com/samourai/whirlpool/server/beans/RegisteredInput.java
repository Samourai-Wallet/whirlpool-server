package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;

public class RegisteredInput {
  private String username;
  private TxOutPoint outPoint;
  private boolean liquidity;
  private String ip;
  private String lastUserHash; // unknown until confirmInput attempt

  public RegisteredInput(
      String username, boolean liquidity, TxOutPoint outPoint, String ip, String lastUserHash) {
    this.username = username;
    this.liquidity = liquidity;
    this.outPoint = outPoint;
    this.ip = ip;
    this.lastUserHash = lastUserHash;
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

  public String getLastUserHash() {
    return lastUserHash;
  }

  public void setLastUserHash(String lastUserHash) {
    this.lastUserHash = lastUserHash;
  }

  @Override
  public String toString() {
    return "outPoint="
        + outPoint
        + ", liquidity="
        + liquidity
        + ", username="
        + username
        + ", ip="
        + ip
        + ",lastUserHash="
        + (lastUserHash != null ? lastUserHash : "null");
  }
}
