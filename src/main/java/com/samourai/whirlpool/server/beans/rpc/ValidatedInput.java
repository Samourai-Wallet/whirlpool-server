package com.samourai.whirlpool.server.beans.rpc;

public class ValidatedInput {
  private int confirmations;
  private long value;
  private String toAddress;

  public ValidatedInput(int confirmations, long value, String toAddress) {
    this.confirmations = confirmations;
    this.value = value;
    this.toAddress = toAddress;
  }

  public int getConfirmations() {
    return confirmations;
  }

  public long getValue() {
    return value;
  }

  public String getToAddress() {
    return toAddress;
  }
}
