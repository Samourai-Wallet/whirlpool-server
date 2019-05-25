package com.samourai.whirlpool.server.services.rpc;

public class RpcRawTransactionResponse {
  private String hex;
  private int confirmations;
  private long blockHeight;

  public RpcRawTransactionResponse(String hex, Integer confirmations, Long blockHeight) {
    this.hex = hex;
    this.confirmations = (confirmations != null ? confirmations : 0);
    this.blockHeight = (blockHeight != null ? blockHeight : 0);
  }

  public String getHex() {
    return hex;
  }

  public int getConfirmations() {
    return confirmations;
  }

  public long getBlockHeight() {
    return blockHeight;
  }
}
