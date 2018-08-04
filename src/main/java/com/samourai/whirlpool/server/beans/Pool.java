package com.samourai.whirlpool.server.beans;

public class Pool {
    private String poolId;
    private long denomination; // in satoshis
    private long minerFee; // in satoshis
    private int minMustMix;
    private int targetAnonymitySet;
    private int minAnonymitySet;
    private int maxAnonymitySet;
    private long timeoutAdjustAnonymitySet; // wait X seconds for decreasing anonymitySet
    private long liquidityTimeout; // wait X seconds for accepting liquidities

    private Mix currentMix;

    public Pool(String poolId, long denomination, long minerFee, int minMustMix, int targetAnonymitySet, int minAnonymitySet, int maxAnonymitySet, long timeoutAdjustAnonymitySet, long liquidityTimeout) {
        this.poolId = poolId;
        this.denomination = denomination;
        this.minerFee = minerFee;
        this.minMustMix = minMustMix;
        this.targetAnonymitySet = targetAnonymitySet;
        this.minAnonymitySet = minAnonymitySet;
        this.maxAnonymitySet = maxAnonymitySet;
        this.timeoutAdjustAnonymitySet = timeoutAdjustAnonymitySet;
        this.liquidityTimeout = liquidityTimeout;
    }

    public String getPoolId() {
        return poolId;
    }

    public long getDenomination() {
        return denomination;
    }

    public long getMinerFee() {
        return minerFee;
    }

    public int getMinMustMix() {
        return minMustMix;
    }

    public int getTargetAnonymitySet() {
        return targetAnonymitySet;
    }

    public void setTargetAnonymitySet(int targetAnonymitySet) {
        this.targetAnonymitySet = targetAnonymitySet;
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
}
