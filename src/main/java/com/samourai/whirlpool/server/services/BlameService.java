package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.persistence.to.BlameTO;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BlameService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private DbService dbService;
  private WhirlpoolServerConfig whirlpoolServerConfig;

  @Autowired
  public BlameService(DbService dbService, WhirlpoolServerConfig whirlpoolServerConfig) {
    this.dbService = dbService;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
  }

  public void blame(ConfirmedInput confirmedInput, BlameReason reason, String mixId) {
    dbService.saveBlame(confirmedInput, reason, mixId, confirmedInput.getRegisteredInput().getIp());
  }

  public boolean isBannedUTXO(String utxoHash, long utxoIndex) {
    List<BlameTO> blames = dbService.findBlames(utxoHash, utxoIndex);
    if (blames.isEmpty()) {
      return false;
    }

    long blamePeriodMs = whirlpoolServerConfig.getBan().getPeriod() * 1000;
    long blameMinTime = System.currentTimeMillis() - blamePeriodMs;

    if (log.isDebugEnabled()) {
      log.debug("blameMinTime=" + blameMinTime);
    }
    int countBlames = 0;
    for (BlameTO blameTO : blames) {
      if (blameTO.getCreated().getTime() >= blameMinTime) {
        countBlames++;
        log.warn("Blame#" + countBlames + ": " + blameTO);
      } else {
        log.warn("Blame(expired): " + blameTO);
      }
    }
    if (countBlames >= whirlpoolServerConfig.getBan().getBlames()) {
      return true;
    }
    return false;
  }
}
