package com.samourai.whirlpool.server.beans;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PoolFee {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private long feeValue; // in satoshis
  private Map<Long, Long> feeAccept; // key=sats, value=maxBlockHeight

  public PoolFee(long feeValue, Map<Long, Long> feeAccept) {
    this.feeValue = feeValue;
    this.feeAccept = (feeAccept != null ? feeAccept : new HashMap<>());
  }

  public boolean checkTx0FeePaid(long tx0FeePaid, long tx0BlockHeight) {
    if (tx0FeePaid >= feeValue) {
      return true;
    }
    Long maxBlockHeight = feeAccept.get(tx0FeePaid);
    if (maxBlockHeight != null) {
      if (tx0BlockHeight <= maxBlockHeight) {
        return true;
      } else {
        log.warn(
            "checkTx0FeePaid: invalid fee payment: feeAccept found for "
                + tx0FeePaid
                + " but tx0BlockHeight="
                + tx0BlockHeight
                + " > maxBlockHeight="
                + tx0BlockHeight);
      }
    }
    log.warn("checkTx0FeePaid: invalid fee payment: " + tx0FeePaid + " < " + feeValue);
    return false;
  }

  public long getFeeValue() {
    return feeValue;
  }

  public Map<Long, Long> getFeeAccept() {
    return feeAccept;
  }

  @Override
  public String toString() {
    return "feeValue=" + feeValue + ", feeAccept=" + feeAccept;
  }
}
