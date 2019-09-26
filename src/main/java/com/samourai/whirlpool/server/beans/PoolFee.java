package com.samourai.whirlpool.server.beans;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PoolFee {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private long feeValue; // in satoshis
  private Map<Long, Long> feeAccept; // key=sats, value=maxTx0Time

  public PoolFee(long feeValue, Map<Long, Long> feeAccept) {
    this.feeValue = feeValue;
    this.feeAccept = (feeAccept != null ? feeAccept : new HashMap<>());
  }

  public boolean checkTx0FeePaid(long tx0FeePaid, long tx0Time, int feeValuePercent) {
    long feeToPay = computeFeeValue(feeValuePercent);
    if (tx0FeePaid >= feeToPay) {
      return true;
    }
    Long maxTxTime = feeAccept.get(tx0FeePaid);
    if (maxTxTime != null) {
      if (tx0Time <= maxTxTime) {
        return true;
      } else {
        log.warn(
            "checkTx0FeePaid: invalid fee payment: feeAccept found for "
                + tx0FeePaid
                + " but tx0Time="
                + tx0Time
                + " > maxTxTime="
                + maxTxTime);
      }
    }
    log.warn("checkTx0FeePaid: invalid fee payment: " + tx0FeePaid + " < " + feeToPay);
    return false;
  }

  public long getFeeValue() {
    return feeValue;
  }

  public Map<Long, Long> getFeeAccept() {
    return feeAccept;
  }

  private long computeFeeValue(int feePercent) {
    int result = Math.round(feeValue * feePercent / 100);
    return result;
  }

  @Override
  public String toString() {
    return "feeValue=" + feeValue + ", feeAccept=" + feeAccept;
  }
}
