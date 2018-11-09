package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.persistence.repositories.MixRepository;
import com.samourai.whirlpool.server.persistence.repositories.RevocationRepository;
import com.samourai.whirlpool.server.persistence.to.BlameTO;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import com.samourai.whirlpool.server.persistence.to.RevocationTO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class DbService {
  private List<BlameTO> blames;
  private MixRepository mixRepository;
  private RevocationRepository revokedBordereauRepository;
  private MixStats mixStats; // cached value

  public DbService(MixRepository mixRepository, RevocationRepository revokedBordereauRepository) {
    this.mixRepository = mixRepository;
    this.revokedBordereauRepository = revokedBordereauRepository;
    __reset(); // TODO
  }

  public void saveBlame(ConfirmedInput confirmedInput, BlameReason blameReason, String mixId) {
    BlameTO blameTO = new BlameTO(confirmedInput, blameReason, mixId);
    blames.add(blameTO);
  }

  public void saveMix(Mix mix) {
    MixTO mixTO = mix.computeMixTO();
    mixRepository.save(mixTO);
    mixStats = null; // clear cache
  }

  // receiveAddress

  public void revokeReceiveAddress(String receiveAddress) {
    RevocationTO revocationTO = new RevocationTO(RevocationType.RECEIVE_ADDRESS, receiveAddress);
    revokedBordereauRepository.save(revocationTO);
  }

  public boolean isRevokedReceiveAddress(String receiveAddress) {
    return revokedBordereauRepository
        .findByRevocationTypeAndValue(RevocationType.RECEIVE_ADDRESS, receiveAddress)
        .isPresent();
  }

  public Iterable<MixTO> findMixs() {
    return mixRepository.findAll(new Sort(Sort.Direction.DESC, "created"));
  }

  public MixStats getMixStats() {
    if (mixStats == null) {
      long nbMixs = mixRepository.countByMixStatus(MixStatus.SUCCESS);
      long sumAmountOut = mixRepository.sumAmountOutByMixStatus(MixStatus.SUCCESS);
      mixStats = new MixStats(nbMixs, sumAmountOut);
    }
    return mixStats;
  }

  public void __reset() {
    blames = new ArrayList<>();
  }
}
