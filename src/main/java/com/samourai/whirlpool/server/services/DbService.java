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
  private FeeValidationService feeValidationService;
  private BlockchainDataService blockchainDataService;

  public DbService(
      MixRepository mixRepository,
      Tx0WhitelistRepository tx0WhitelistRepository,
      MixOutputRepository mixOutputRepository,
      MixTxidRepository mixTxidRepository,
      BlameRepository blameRepository,
      FeeValidationService feeValidationService,
      BlockchainDataService blockchainDataService) {
    this.mixRepository = mixRepository;
    this.tx0WhitelistRepository = tx0WhitelistRepository;
    this.mixOutputRepository = mixOutputRepository;
    this.mixTxidRepository = mixTxidRepository;
    this.blameRepository = blameRepository;
    this.feeValidationService = feeValidationService;
    this.blockchainDataService = blockchainDataService;
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

  public void saveBlame(
      ConfirmedInput confirmedInput, BlameReason reason, String mixId, String ip) {
    TxOutPoint txOutPoint = confirmedInput.getRegisteredInput().getOutPoint();
    String identifier = computeBlameIdentitifer(txOutPoint.getHash(), txOutPoint.getIndex());
    BlameTO blameTO = new BlameTO(identifier, reason, mixId, ip);
    log.warn("+blame: " + blameTO);
    blameRepository.save(blameTO);
  }

  public List<BlameTO> findBlames(String utxoHash, long utxoIndex) {
    String identifier = computeBlameIdentitifer(utxoHash, utxoIndex);
    return blameRepository.findByIdentifier(identifier);
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

  public void __reset() {
    // TODO for tests only!
    mixRepository.deleteAll();
    tx0WhitelistRepository.deleteAll();
    mixOutputRepository.deleteAll();
    mixTxidRepository.deleteAll();
    blameRepository.deleteAll();
  }
}
