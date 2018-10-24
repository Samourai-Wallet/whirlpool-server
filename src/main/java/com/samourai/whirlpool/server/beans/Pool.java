package com.samourai.whirlpool.server.beans;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;

public class Pool {
  private String poolId;
  private long denomination; // in satoshis
  private long minerFeeMin; // in satoshis
  private long minerFeeMax; // in satoshis
  private int minMustMix;
  private int targetAnonymitySet;
  private int minAnonymitySet;
  private int maxAnonymitySet;
  private long timeoutAdjustAnonymitySet; // wait X seconds for decreasing anonymitySet
  private long liquidityTimeout; // wait X seconds for accepting liquidities

  private Mix currentMix;
  private InputPool mustMixQueue;
  private InputPool liquidityQueue;
  private InputPool unconfirmedQueue;

  public Pool(
      String poolId,
      long denomination,
      long minerFeeMin,
      long minerFeeMax,
      int minMustMix,
      int targetAnonymitySet,
      int minAnonymitySet,
      int maxAnonymitySet,
      long timeoutAdjustAnonymitySet,
      long liquidityTimeout) {
    this.poolId = poolId;
    this.denomination = denomination;
    this.minerFeeMin = minerFeeMin;
    this.minerFeeMax = minerFeeMax;
    this.minMustMix = minMustMix;
    this.targetAnonymitySet = targetAnonymitySet;
    this.minAnonymitySet = minAnonymitySet;
    this.maxAnonymitySet = maxAnonymitySet;
    this.timeoutAdjustAnonymitySet = timeoutAdjustAnonymitySet;
    this.liquidityTimeout = liquidityTimeout;
    this.mustMixQueue = new InputPool();
    this.liquidityQueue = new InputPool();
    this.unconfirmedQueue = new InputPool();
  }

  public boolean checkInputBalance(long inputBalance, boolean liquidity) {
    long minBalance = computeInputBalanceMin(liquidity);
    long maxBalance = computeInputBalanceMax(liquidity);
    return inputBalance >= minBalance && inputBalance <= maxBalance;
  }

  public long computeInputBalanceMin(boolean liquidity) {
    return WhirlpoolProtocol.computeInputBalanceMin(denomination, liquidity, minerFeeMin);
  }

  public long computeInputBalanceMax(boolean liquidity) {
    return WhirlpoolProtocol.computeInputBalanceMax(denomination, liquidity, minerFeeMax);
  }

  public String getPoolId() {
    return poolId;
  }

  public long getDenomination() {
    return denomination;
  }

  public long getMinerFeeMin() {
    return minerFeeMin;
  }

  public long getMinerFeeMax() {
    return minerFeeMax;
  }

  public int getMinMustMix() {
    return minMustMix;
  }

  public int getTargetAnonymitySet() {
    return targetAnonymitySet;
  }

  public int getMinAnonymitySet() {
    return minAnonymitySet;
  }

  public int getMaxAnonymitySet() {
    return maxAnonymitySet;
  }

  public long getTimeoutAdjustAnonymitySet() {
    return timeoutAdjustAnonymitySet;
  }

  public long getLiquidityTimeout() {
    return liquidityTimeout;
  }

  public Mix getCurrentMix() {
    return currentMix;
  }

  public void setCurrentMix(Mix currentMix) {
    this.currentMix = currentMix;
  }

  public InputPool getMustMixQueue() {
    return mustMixQueue;
  }

  public InputPool getLiquidityQueue() {
    return liquidityQueue;
  }

  public InputPool getUnconfirmedQueue() {
    return unconfirmedQueue;
  }
}
