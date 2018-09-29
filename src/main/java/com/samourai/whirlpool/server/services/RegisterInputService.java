package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalBordereauException;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.MixException;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;

@Service
public class RegisterInputService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private PoolService poolService;
    private CryptoService cryptoService;
    private BlockchainService blockchainService;
    private BlameService blameService;
    private WhirlpoolServerConfig whirlpoolServerConfig;

    @Autowired
    public RegisterInputService(PoolService poolService, CryptoService cryptoService, BlockchainService blockchainService, BlameService blameService, WhirlpoolServerConfig whirlpoolServerConfig) {
        this.poolService = poolService;
        this.cryptoService = cryptoService;
        this.blockchainService = blockchainService;
        this.blameService = blameService;
        this.whirlpoolServerConfig = whirlpoolServerConfig;
    }

    public synchronized void registerInput(String poolId, String username, byte[] pubkey, String signature, String utxoHash, long utxoIndex, boolean liquidity, boolean testMode) throws IllegalInputException, IllegalBordereauException, MixException {
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
        checkInputSignature(poolId, pubkey, signature);

        // verify input is a valid mustMix or liquidity
        TxOutPoint txOutPoint = blockchainService.validateAndGetPremixInput(utxoHash, utxoIndex, pubkey, liquidity, testMode);

        // register input to pool
        poolService.registerInput(poolId, username, pubkey, liquidity, txOutPoint);
    }

    private void checkInputSignature(String message, byte[] pubkeyHex, String signature) throws IllegalInputException {
        if (log.isDebugEnabled()) {
            log.debug("Verifying signature: " + signature + "\n  for pubkey: " + Utils.HEX.encode(pubkeyHex) + "\n  for message: " + message);
        }

        // verify signature of message for pubkey
        if (!cryptoService.verifyMessageSignature(pubkeyHex, message, signature)) {
            throw new IllegalInputException("Invalid signature");
        }
    }

}
