package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.MixStats;
import com.samourai.whirlpool.server.persistence.repositories.MixOutputRepository;
import com.samourai.whirlpool.server.persistence.repositories.MixRepository;
import com.samourai.whirlpool.server.persistence.repositories.MixTxidRepository;
import com.samourai.whirlpool.server.persistence.to.BlameTO;
import com.samourai.whirlpool.server.persistence.to.MixOutputTO;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import com.samourai.whirlpool.server.persistence.to.MixTxidTO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class DbService {
  private List<BlameTO> blames;
  private MixRepository mixRepository;
  private MixOutputRepository mixOutputRepository;
  private MixTxidRepository mixTxidRepository;
  private MixStats mixStats; // cached value

  public DbService(MixRepository mixRepository, MixOutputRepository mixOutputRepository, MixTxidRepository mixTxidRepository) {
    this.mixRepository = mixRepository;
    this.mixOutputRepository = mixOutputRepository;
    this.mixTxidRepository = mixTxidRepository;
    __reset(); // TODO
  }

  public void saveBlame(ConfirmedInput confirmedInput, BlameReason blameReason, String mixId) {
    BlameTO blameTO = new BlameTO(confirmedInput, blameReason, mixId);
    blames.add(blameTO);
  }

  // mix

  public void saveMix(Mix mix) {
    MixTO mixTO = mix.computeMixTO();
    mixRepository.save(mixTO);
    mixStats = null; // clear cache
  }

  public MixStats getMixStats() {
    if (mixStats == null) {
      long nbMixs = mixRepository.countByMixStatus(MixStatus.SUCCESS);
      long sumAmountOut = mixRepository.sumAmountOutByMixStatus(MixStatus.SUCCESS);
      mixStats = new MixStats(nbMixs, sumAmountOut);
    }
    return mixStats;
  }

  // output

  public void saveMixOutput(String outputAddress) {
    MixOutputTO mixOutputTO = new MixOutputTO(outputAddress);
    mixOutputRepository.save(mixOutputTO);
  }

  public boolean hasMixOutput(String receiveAddress) {
    return mixOutputRepository
        .findByAddress(receiveAddress)
        .isPresent();
  }

  // txid

  public void saveMixTxid(String txid, long denomination) {
    MixTxidTO mixTxidTO = new MixTxidTO(txid, denomination);
    mixTxidRepository.save(mixTxidTO);
  }

  public boolean hasMixTxid(String txid, long denomination) {
    return mixTxidRepository
        .findByTxidAndDenomination(txid, denomination)
        .isPresent();
  }

  public Page<MixTO> findMixs(Pageable pageable) {
    return mixRepository.findAll(pageable);
  }

  public void __reset() {
    blames = new ArrayList<>();
  }
}
