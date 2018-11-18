package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.MixStats;
import com.samourai.whirlpool.server.persistence.repositories.MixOutputRepository;
import com.samourai.whirlpool.server.persistence.repositories.MixRepository;
import com.samourai.whirlpool.server.persistence.to.BlameTO;
import com.samourai.whirlpool.server.persistence.to.MixOutputTO;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class DbService {
  private List<BlameTO> blames;
  private MixRepository mixRepository;
  private MixOutputRepository revokedBordereauRepository;
  private MixStats mixStats; // cached value

  public DbService(MixRepository mixRepository, MixOutputRepository revokedBordereauRepository) {
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

  // output

  public void revokeOutput(String outputAddress) {
    MixOutputTO mixOutputTO = new MixOutputTO(outputAddress);
    revokedBordereauRepository.save(mixOutputTO);
  }

  public boolean isRevokedOutput(String receiveAddress) {
    return revokedBordereauRepository
        .findByAddress(receiveAddress)
        .isPresent();
  }

  public Page<MixTO> findMixs(Pageable pageable) {
    return mixRepository.findAll(pageable);
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
