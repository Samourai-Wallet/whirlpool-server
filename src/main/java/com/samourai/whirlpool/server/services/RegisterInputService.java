package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.*;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;

@Service
public class RegisterInputService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private MixService mixService;
    private CryptoService cryptoService;
    private BlockchainService blockchainService;
    private DbService dbService;
    private BlameService blameService;
    private WhirlpoolServerConfig whirlpoolServerConfig;

    @Autowired
    public RegisterInputService(MixService mixService, CryptoService cryptoService, BlockchainService blockchainService, DbService dbService, BlameService blameService, WhirlpoolServerConfig whirlpoolServerConfig) {
        this.mixService = mixService;
        this.cryptoService = cryptoService;
        this.blockchainService = blockchainService;
        this.dbService = dbService;
        this.blameService = blameService;
        this.whirlpoolServerConfig = whirlpoolServerConfig;
    }

    public synchronized void registerInput(String mixId, String username, byte[] pubkey, String signature, byte[] blindedBordereau, String utxoHash, long utxoIndex, boolean liquidity, boolean testMode) throws IllegalInputException, IllegalBordereauException, MixException {
        // verify UTXO not banned
        if (blameService.isBannedUTXO(utxoHash, utxoIndex)) {
            log.warn("Rejecting banned UTXO: "+utxoHash+":"+utxoIndex);
            throw new IllegalBordereauException("Banned from service");
        }

        // validate input
        try {
            TxOutPoint txOutPoint = blockchainService.validateAndGetPremixInput(utxoHash, utxoIndex, pubkey, liquidity, testMode);

            // verify signature
            checkInputSignature(mixId, pubkey, signature);

            // register input and send back signedBordereau
            mixService.registerInput(mixId, username, txOutPoint, pubkey, blindedBordereau, liquidity);

        } catch(UnconfirmedInputException e) {
            // queue unconfirmed input
            mixService.responseQueueInput(username, mixId);
        }
    }

    private void checkInputSignature(String mixId, byte[] pubkeyHex, String signature) throws IllegalInputException {
        if (log.isDebugEnabled()) {
            log.debug("Verifying signature: " + signature + "\n  for pubkey: " + Utils.HEX.encode(pubkeyHex) + "\n  for mixId: " + mixId);
        }

        // verify signature of 'mixId' for pubkey
        if (!cryptoService.verifyMessageSignature(pubkeyHex, mixId, signature)) {
            throw new IllegalInputException("Invalid signature");
        }
    }

}
