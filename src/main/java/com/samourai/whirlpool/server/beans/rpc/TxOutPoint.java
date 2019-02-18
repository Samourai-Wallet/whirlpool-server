package com.samourai.whirlpool.server.beans.rpc;

public class TxOutPoint {
  private String hash;
  private long index;
  private long value;
  private int confirmations;
  private byte[] scriptBytes;
  private String toAddress;

  public TxOutPoint(
      String hash,
      long index,
      long value,
      int confirmations,
      byte[] scriptBytes,
      String toAddress) {
    this.hash = hash;
    this.index = index;
    this.value = value;
    this.confirmations = confirmations;
    this.scriptBytes = scriptBytes;
    this.toAddress = toAddress;
  }

  public String getHash() {
    return hash;
  }

  public long getIndex() {
    return index;
  }

  public long getValue() {
    return value;
  }

  public int getConfirmations() {
    return confirmations;
  }

  public byte[] getScriptBytes() {
    return scriptBytes;
  }

  public String getToAddress() {
    return toAddress;
  }

  public String toKey() {
    return hash + ":" + index;
  }

  @Override
  public String toString() {
    return toKey() + " (" + value + "sats, " + confirmations + " confirmations)";
  }
}
