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

    private MixService mixService;
    private CryptoService cryptoService;
    private DbService dbService;
    private FormatsUtil formatsUtil;

    @Autowired
    public RegisterOutputService(MixService mixService, CryptoService cryptoService, DbService dbService, FormatsUtil formatsUtil) {
        this.mixService = mixService;
        this.cryptoService = cryptoService;
        this.dbService = dbService;
        this.formatsUtil = formatsUtil;
    }

    public void registerOutput(String mixId, byte[] unblindedSignedBordereau, String bordereau, String receiveAddress) throws Exception {
        // validate
        validate(unblindedSignedBordereau, bordereau, receiveAddress);

        // register
        mixService.registerOutput(mixId, receiveAddress, bordereau);

        // register bordereau
        dbService.registerBordereau(bordereau);
    }

    private void validate(byte[] unblindedSignedBordereau, String bordereau, String receiveAddress) throws Exception {
        // verify bordereau
        if (log.isDebugEnabled()) {
            log.debug("Verifying bordereau: " + bordereau + " : " + Base64.encodeBase64String(unblindedSignedBordereau));
        }
        if (!cryptoService.verifyUnblindedSignedBordereau(bordereau, unblindedSignedBordereau)) {
            throw new Exception("Invalid unblindedBordereau");
        }

        // verify output
        if (!formatsUtil.isValidBech32(receiveAddress)) {
            throw new Exception("Invalid receiveAddress");
        }

        // verify blindedBordereau never registered
        if (dbService.isBordereauRegistered(bordereau)) {
            throw new IllegalBordereauException("bordereau already registered");
        }
    }

}
