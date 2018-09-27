package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalBordereauException;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.exceptions.UnconfirmedInputException;
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
        if (!cryptoService.isValidTxHash(utxoHash)) {
            throw new IllegalInputException("Invalid utxoHash");
        }
        if (utxoIndex < 0) {
            throw new IllegalInputException("Invalid utxoIndex");
        }

        // verify UTXO not banned
        if (blameService.isBannedUTXO(utxoHash, utxoIndex)) {
            log.warn("Rejecting banned UTXO: "+utxoHash+":"+utxoIndex);
            throw new IllegalBordereauException("Banned from service");
        }

        // verify signature
        checkInputSignature(mixId, pubkey, signature);

        try {
            // verify utxo & confirmations
            TxOutPoint txOutPoint = blockchainService.validateAndGetPremixInput(utxoHash, utxoIndex, pubkey, liquidity, testMode);
            RegisteredInput registeredInput = new RegisteredInput(username, pubkey, blindedBordereau, liquidity, txOutPoint);

            // register input and send back signedBordereau
            mixService.registerInput(mixId, registeredInput);

        } catch(UnconfirmedInputException e) {
            // queue unconfirmed input
            TxOutPoint unconfirmedOutpoint = e.getUnconfirmedOutPoint();
            RegisteredInput unconfirmedInput = new RegisteredInput(username, pubkey, blindedBordereau, liquidity, unconfirmedOutpoint);
            mixService.queueUnconfirmedInput(mixId, unconfirmedInput, e.getMessage());
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
