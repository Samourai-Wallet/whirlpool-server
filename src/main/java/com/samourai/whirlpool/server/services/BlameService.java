package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.fee.WhirlpoolFeeData;
import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.persistence.to.BlameTO;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BlameService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private DbService dbService;
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private FeeValidationService feeValidationService;
  private BlockchainDataService blockchainDataService;

  @Autowired
  public BlameService(
      DbService dbService,
      WhirlpoolServerConfig whirlpoolServerConfig,
      FeeValidationService feeValidationService,
      BlockchainDataService blockchainDataService) {
    this.dbService = dbService;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.feeValidationService = feeValidationService;
    this.blockchainDataService = blockchainDataService;
  }

  public void blame(ConfirmedInput confirmedInput, BlameReason reason, String mixId) {
    TxOutPoint txOutPoint = confirmedInput.getRegisteredInput().getOutPoint();
    String identifier = computeBlameIdentitifer(txOutPoint.getHash(), txOutPoint.getIndex());
    blame(identifier, reason, mixId, confirmedInput.getRegisteredInput().getIp());
  }

  protected BlameTO blame(String identifier, BlameReason reason, String mixId, String ip) {
    return dbService.saveBlame(identifier, reason, mixId, ip);
  }

  public boolean isBannedUTXO(String utxoHash, long utxoIndex) {
    String identifier = computeBlameIdentitifer(utxoHash, utxoIndex);
    return isBannedUTXO(identifier);
  }

  protected boolean isBannedUTXO(String identifier) {
    int maxBlames = whirlpoolServerConfig.getBan().getBlames();

    long blamePeriodMs = whirlpoolServerConfig.getBan().getPeriod() * 1000;
    long blameExpirationMs = whirlpoolServerConfig.getBan().getExpiration() * 1000;
    Timestamp blameExpirationMinTime =
        new Timestamp(System.currentTimeMillis() - (Math.max(blamePeriodMs, blameExpirationMs)));

    // ignore expired blames
    List<BlameTO> blamesNotExpired =
        dbService.findBlamesByDateMin(identifier, blameExpirationMinTime);

    if (blamesNotExpired.size() < maxBlames) {
      // not banned yet
      if (blamesNotExpired.size() > 0) {
        if (log.isDebugEnabled()) {
          log.debug(blamesNotExpired.size() + " blames found for " + identifier);
        }
      }
      return false;
    }

    // check period
    for (BlameTO blameNotExpired : blamesNotExpired) {
      // find blames in same period
      long periodMinTime = blameNotExpired.getCreated().getTime();
      long periodMaxTime = periodMinTime + blamePeriodMs;
      List<BlameTO> blamesInPeriod =
          blamesNotExpired
              .stream()
              .filter(
                  blameTO ->
                      blameTO.getCreated().getTime() <= periodMaxTime
                          && blameTO.getCreated().getTime() >= periodMinTime)
              .collect(Collectors.toList());
      if (blamesInPeriod.size() >= maxBlames) {
        log.warn(
            blamesInPeriod.size()
                + " blames found for period ["
                + periodMinTime
                + ";"
                + periodMaxTime
                + "]");
        if (log.isDebugEnabled()) {
          int i = 0;
          for (BlameTO b : blamesInPeriod) {
            log.warn("Blame#" + i + ": " + b);
            i++;
          }
        }
        return true;
      }
    }
    return false;
  }

  private String computeBlameIdentitifer(String myUtxoHash, long utxoIndex) {
    // ban UTXO by default
    final String utxoHash = myUtxoHash.trim().toLowerCase();
    String blameIdentifier = Utils.computeInputId(utxoHash, utxoIndex);

    // is it a tx0?
    try {
      RpcTransaction tx =
          blockchainDataService
              .getRpcTransaction(utxoHash)
              .orElseThrow(() -> new Exception("utxoHash not found: " + utxoHash));

      WhirlpoolFeeData feeData = feeValidationService.decodeFeeData(tx.getTx());
      if (feeData != null) {
        // this is a tx0 => ban tx0
        blameIdentifier = utxoHash;
      }
    } catch (Exception e) {
      // system error
      log.error("", e);
    }
    return blameIdentifier;
  }
}
