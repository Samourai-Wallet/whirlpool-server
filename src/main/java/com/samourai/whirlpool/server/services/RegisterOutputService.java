package com.samourai.whirlpool.server.services;

import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.server.exceptions.IllegalBordereauException;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;

@Service
public class RegisterOutputService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private RoundService roundService;
    private CryptoService cryptoService;
    private DbService dbService;
    private FormatsUtil formatsUtil;

    @Autowired
    public RegisterOutputService(RoundService roundService, CryptoService cryptoService, DbService dbService, FormatsUtil formatsUtil) {
        this.roundService = roundService;
        this.cryptoService = cryptoService;
        this.dbService = dbService;
        this.formatsUtil = formatsUtil;
    }

    public void registerOutput(String roundId, byte[] unblindedSignedBordereau, String bordereau, String sendAddress, String receiveAddress) throws Exception {
        // validate
        validate(unblindedSignedBordereau, bordereau, sendAddress, receiveAddress);

        // register
        roundService.registerOutput(roundId, sendAddress, receiveAddress, bordereau);

        // register bordereau
        dbService.registerBordereau(bordereau);
    }

    private void validate(byte[] unblindedSignedBordereau, String bordereau, String sendAddress, String receiveAddress) throws Exception {
        // verify bordereau
        log.info("Verifying bordereau: "+bordereau+" : "+Base64.encodeBase64String(unblindedSignedBordereau));
        if (!cryptoService.verifyUnblindedSignedBordereau(bordereau, unblindedSignedBordereau)) {
            throw new Exception("Invalid unblindedBordereau");
        }

        // verify output
        if (!formatsUtil.isValidBech32(sendAddress)) {
            throw new Exception("Invalid sendAddress");
        }
        if (!formatsUtil.isValidBech32(receiveAddress)) {
            throw new Exception("Invalid receiveAddress");
        }

        // verify blindedBordereau never registered
        if (dbService.isBordereauRegistered(bordereau)) {
            throw new IllegalBordereauException("bordereau already registered");
        }
    }

}
