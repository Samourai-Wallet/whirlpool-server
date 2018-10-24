package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import java.lang.invoke.MethodHandles;
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
    log.info("blameForNoSigning " + confirmedInput.getRegisteredInput().getUsername());
    dbService.saveBlame(confirmedInput, reason, mixId);
  }

  public boolean isBannedUTXO(String utxoHash, long utxoIndex) {
    // TODO
    int countBlames = 0; // dbService.countBlamesByUTXO(utxoHash, utxoIndex);
    return countBlames >= whirlpoolServerConfig.getBan().getBlames();
  }
}
