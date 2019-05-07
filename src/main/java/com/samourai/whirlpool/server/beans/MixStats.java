package com.samourai.whirlpool.server.beans;

public class MixStats {
  private long nbMixs;
  private long sumMustMix;
  private long sumAmountOut;

  public MixStats(long nbMixs, long sumMustMix, long sumAmountOut) {
    this.nbMixs = nbMixs;
    this.sumMustMix = sumMustMix;
    this.sumAmountOut = sumAmountOut;
  }

  public long getNbMixs() {
    return nbMixs;
  }

  public long getSumMustMix() {
    return sumMustMix;
  }

  public long getSumAmountOut() {
    return sumAmountOut;
  }
}
