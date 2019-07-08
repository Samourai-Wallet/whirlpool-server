package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.fee.WhirlpoolFeeData;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.persistence.repositories.*;
import com.samourai.whirlpool.server.persistence.to.BlameTO;
import com.samourai.whirlpool.server.persistence.to.MixOutputTO;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import com.samourai.whirlpool.server.persistence.to.MixTxidTO;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class DbService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private MixRepository mixRepository;
  private Tx0WhitelistRepository tx0WhitelistRepository;
  private MixOutputRepository mixOutputRepository;
  private MixTxidRepository mixTxidRepository;
  private MixStats mixStats; // cached value
  private BlameRepository blameRepository;

  public DbService(
      MixRepository mixRepository,
      Tx0WhitelistRepository tx0WhitelistRepository,
      MixOutputRepository mixOutputRepository,
      MixTxidRepository mixTxidRepository,
      BlameRepository blameRepository) {
    this.mixRepository = mixRepository;
    this.tx0WhitelistRepository = tx0WhitelistRepository;
    this.mixOutputRepository = mixOutputRepository;
    this.mixTxidRepository = mixTxidRepository;
    this.blameRepository = blameRepository;
  }

  // mix

  public void saveMix(Mix mix) {
    MixTO mixTO = mix.computeMixTO();
    mixRepository.save(mixTO);
    mixStats = null; // clear cache
  }

  public MixStats getMixStats() {
    if (mixStats == null) {
      long nbMixs = zeroIfNull(mixRepository.countByMixStatus(MixStatus.SUCCESS));
      long sumMustMix = zeroIfNull(mixRepository.sumMustMixByMixStatus(MixStatus.SUCCESS));
      long sumAmountOut = zeroIfNull(mixRepository.sumAmountOutByMixStatus(MixStatus.SUCCESS));
      mixStats = new MixStats(nbMixs, sumMustMix, sumAmountOut);
    }
    return mixStats;
  }

  private long zeroIfNull(Long value) {
    return value != null ? value : 0;
  }

  // tx0Whitelist

  public boolean hasTx0Whitelist(String txid) {
    return tx0WhitelistRepository.findByTxid(txid).isPresent();
  }

  // output

  public void saveMixOutput(String outputAddress) {
    MixOutputTO mixOutputTO = new MixOutputTO(outputAddress);
    mixOutputRepository.save(mixOutputTO);
  }

  public boolean hasMixOutput(String receiveAddress) {
    return mixOutputRepository.findByAddress(receiveAddress).isPresent();
  }

  // txid

  public void saveMixTxid(String txid, long denomination) {
    MixTxidTO mixTxidTO = new MixTxidTO(txid, denomination);
    mixTxidRepository.save(mixTxidTO);
  }

  public boolean hasMixTxid(String txid, long denomination) {
    return mixTxidRepository.findByTxidAndDenomination(txid, denomination).isPresent();
  }

  public Page<MixTO> findMixs(Pageable pageable) {
    return mixRepository.findAll(pageable);
  }

  // blame

  public BlameTO saveBlame(String identifier, BlameReason reason, String mixId, String ip) {
    BlameTO blameTO = new BlameTO(identifier, reason, mixId, ip);
    log.warn("+blame: " + blameTO);
    return blameRepository.save(blameTO);
  }

  public List<BlameTO> findBlamesOrderByCreatedAsc(String identifier) {
    return blameRepository.findByIdentifierOrderByCreatedAsc(identifier);
  }

  public void __reset() {
    // TODO for tests only!
    mixRepository.deleteAll();
    tx0WhitelistRepository.deleteAll();
    mixOutputRepository.deleteAll();
    mixTxidRepository.deleteAll();
    blameRepository.deleteAll();
  }
}
