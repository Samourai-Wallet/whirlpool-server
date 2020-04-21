package com.samourai.whirlpool.server.services.fee;

public class WhirlpoolFeeData {

  private int feeIndice;
  private short scodePayload;
  private short partnerPayload;

  public WhirlpoolFeeData(int feeIndice, short scodePayload, short partnerPayload) {
    this.feeIndice = feeIndice;
    this.scodePayload = scodePayload;
    this.partnerPayload = partnerPayload;
  }

  public int getFeeIndice() {
    return feeIndice;
  }

  public short getScodePayload() {
    return scodePayload;
  }

  public short getPartnerPayload() {
    return partnerPayload;
  }
}
