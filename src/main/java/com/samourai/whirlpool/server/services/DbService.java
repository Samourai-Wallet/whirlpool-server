package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.persistence.repositories.MixRepository;
import com.samourai.whirlpool.server.persistence.repositories.RevocationRepository;
import com.samourai.whirlpool.server.persistence.to.BlameTO;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import com.samourai.whirlpool.server.persistence.to.RevocationTO;
import com.samourai.whirlpool.server.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class DbService {
  private List<BlameTO> blames;
  private MixRepository mixRepository;
  private RevocationRepository revokedBordereauRepository;

  public DbService(MixRepository mixRepository, RevocationRepository revokedBordereauRepository) {
    this.mixRepository = mixRepository;
    this.revokedBordereauRepository = revokedBordereauRepository;
    __reset(); // TODO
  }

  private String computeKeyBlindedBordereau(byte[] blindedBordereau) {
    return Utils.sha512Hex(blindedBordereau);
  }

  public void saveBlame(ConfirmedInput confirmedInput, BlameReason blameReason, String mixId) {
    BlameTO blameTO = new BlameTO(confirmedInput, blameReason, mixId);
    blames.add(blameTO);
  }

  public void saveMix(Mix mix) {
    MixTO mixTO = mix.computeMixTO();
    mixRepository.save(mixTO);
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

  public void __reset() {
    blames = new ArrayList<>();
  }
}
