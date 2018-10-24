package com.samourai.whirlpool.server.services;

import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SigningService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private MixService mixService;

  @Autowired
  public SigningService(MixService mixService) {
    this.mixService = mixService;
  }

  public void signing(String mixId, String username, byte[][] witness) throws Exception {
    // signing
    mixService.registerSignature(mixId, username, witness);
  }
}
