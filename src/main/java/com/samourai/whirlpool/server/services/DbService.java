package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.MixStats;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.persistence.repositories.*;
import com.samourai.whirlpool.server.persistence.to.*;
import java.lang.invoke.MethodHandles;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.params.MainNetParams;
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
  private BanRepository banRepository;
  private BlockchainDataService blockchainDataService;
  private Map<String, RpcTransaction> cacheTx;

  public DbService(
      MixRepository mixRepository,
      Tx0WhitelistRepository tx0WhitelistRepository,
      MixOutputRepository mixOutputRepository,
      MixTxidRepository mixTxidRepository,
      BlameRepository blameRepository,
      BanRepository banRepository,
      BlockchainDataService blockchainDataService) {
    this.mixRepository = mixRepository;
    this.tx0WhitelistRepository = tx0WhitelistRepository;
    this.mixOutputRepository = mixOutputRepository;
    this.mixTxidRepository = mixTxidRepository;
    this.blameRepository = blameRepository;
    this.banRepository = banRepository;
    this.blockchainDataService = blockchainDataService;
    this.cacheTx = new HashMap<>();
  }

  public void fixMixHistory() {
    List<MixTO> mixsToFix = new ArrayList<>();
    for (MixTO mixTO : mixRepository.findAll()) {
      if (MixStatus.SUCCESS.equals(mixTO.getMixStatus()) && mixTO.getAnonymitySet() != 5) {
        mixsToFix.add(mixTO);
      }
    }
    log.info(mixsToFix.size() + " mixs to fix");
    String sqls = "";
    for (MixTO mixTO : mixsToFix) {
      try {
        sqls += fixMix(mixTO) + "\n";
      } catch (Exception e) {
        log.error(mixTO.getMixId(), e);
      }
    }
    log.info("*** SQLS ***");
    log.info(sqls);
  }

  private RpcTransaction getTx(String txid) throws Exception {
    RpcTransaction tx = cacheTx.get(txid);
    if (tx == null) {
      tx = blockchainDataService.getRpcTransaction(txid).get();
      cacheTx.put(txid, tx);
    }
    return tx;
  }

  private long getInputValue(TransactionOutPoint outPoint) {
    try {
      RpcTransaction tx = getTx(outPoint.getHash().toString());
      TxOutPoint out = blockchainDataService.getOutPoint(tx, outPoint.getIndex());
      return out.getValue();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String fixMix(MixTO mix) throws Exception {
    MixLogTO mixLog = mix.getMixLog();
    if (mixLog == null) {
      throw new Exception("MixLog is NULL for " + mix.getMixId());
    }

    Transaction tx =
        new Transaction(MainNetParams.get(), org.bitcoinj.core.Utils.HEX.decode(mixLog.getRawTx()));
    log.info("tx: " + tx.getHashAsString());
    if (tx.getInputs().size() != 5 || tx.getOutputs().size() != 5) {
      throw new Exception("tx size error for " + mix.getMixId());
    }

    int nbMustMix = 0;
    int nbLiquidity = 0;
    long amountIn = 0;
    for (TransactionInput in : tx.getInputs()) {
      TransactionOutPoint outPoint = in.getOutpoint();
      long inputValue = getInputValue(outPoint);
      // log.debug("in[" + outPoint + "] =" + inputValue);
      if (inputValue == mix.getDenomination()) {
        nbLiquidity++;
      } else if (inputValue > mix.getDenomination()) {
        nbMustMix++;
      } else {
        log.error("input value error");
      }
      amountIn += inputValue;
    }

    long amountOut =
        tx.getOutputs().stream().map(in -> in.getValue().getValue()).reduce(0L, Long::sum);

    log.info("* " + mix);
    mix.fix(nbMustMix, nbLiquidity, amountIn, amountOut);
    log.info("> " + mix);
    String sql =
        "UPDATE mix SET nb_liquidities="
            + nbLiquidity
            + ", nb_must_mix="
            + nbMustMix
            + ", amount_in="
            + amountIn
            + ", amount_out="
            + amountOut
            + ", anonymity_set=5 where mix_id='"
            + mix.getMixId()
            + "';";
    log.info(sql);
    return sql;
    // mixRepository.save(mixTO);
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

  public List<BlameTO> findBlames(String identifier) {
    return blameRepository.findBlamesByIdentifierOrderByCreatedAsc(identifier);
  }

  // ban

  public BanTO saveBan(String identifier, Timestamp expiration, String response, String notes) {
    BanTO banTO = new BanTO(identifier, expiration, response, notes);
    log.warn("+ban: " + banTO);
    return banRepository.save(banTO);
  }

  public List<BanTO> findByIdentifierAndExpirationAfterOrNull(
      String identifier, Timestamp expirationMin) {
    return banRepository.findByIdentifierAndExpirationAfterOrNull(identifier, expirationMin);
  }

  public Page<BanTO> findByExpirationAfterOrNull(Timestamp expirationMin, Pageable pageable) {
    return banRepository.findByExpirationAfterOrNull(expirationMin, pageable);
  }

  public void __reset() {
    // TODO for tests only!
    mixRepository.deleteAll();
    tx0WhitelistRepository.deleteAll();
    mixOutputRepository.deleteAll();
    mixTxidRepository.deleteAll();
    blameRepository.deleteAll();
    banRepository.deleteAll();
  }
}
