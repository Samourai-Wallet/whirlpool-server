package com.samourai.whirlpool.server.beans;

public class MixStats {
  private long nbMixs;
  private long sumAmountOut;

  public MixStats(long nbMixs, long sumAmountOut) {
    this.nbMixs = nbMixs;
    this.sumAmountOut = sumAmountOut;
  }

  public long getNbMixs() {
    return nbMixs;
  }

  public long getSumAmountOut() {
    return sumAmountOut;
  }
}
