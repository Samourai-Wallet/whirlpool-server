package com.samourai.whirlpool.server.services.rpc;

public class RpcRawTransactionResponse {
  private String hex;
  private int confirmations;
  private long txTime;

  public RpcRawTransactionResponse(String hex, Integer confirmations, Long txTime) {
    this.hex = hex;
    this.confirmations = (confirmations != null ? confirmations : 0);
    this.txTime = (txTime != null ? txTime : 0);
  }

  public String getHex() {
    return hex;
  }

  public int getConfirmations() {
    return confirmations;
  }

  public long getTxTime() {
    return txTime;
  }
}
