package com.samourai.whirlpool.server.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;

@Service
public class SigningService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private RoundService roundService;

    @Autowired
    public SigningService(RoundService roundService) {
        this.roundService = roundService;
    }

    public void signing(String roundId, String username, byte[][] witness) throws Exception {
        // signing
        roundService.registerSignature(roundId, username, witness);
    }

}
