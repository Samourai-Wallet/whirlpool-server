package com.samourai.whirlpool.server.services;

import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalBordereauException;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.RoundException;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;

@Service
public class RegisterInputService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private RoundService roundService;
    private CryptoService cryptoService;
    private BlockchainService blockchainService;
    private DbService dbService;
    private BlameService blameService;
    private FormatsUtil formatsUtil;
    private WhirlpoolServerConfig whirlpoolServerConfig;

    @Autowired
    public RegisterInputService(RoundService roundService, CryptoService cryptoService, BlockchainService blockchainService, DbService dbService, BlameService blameService, FormatsUtil formatsUtil, WhirlpoolServerConfig whirlpoolServerConfig) {
        this.roundService = roundService;
        this.cryptoService = cryptoService;
        this.blockchainService = blockchainService;
        this.dbService = dbService;
        this.blameService = blameService;
        this.formatsUtil = formatsUtil;
        this.whirlpoolServerConfig = whirlpoolServerConfig;
    }

    public synchronized void registerInput(String roundId, String username, byte[] pubkey, String signature, byte[] blindedBordereau, String utxoHash, long utxoIndex, String paymentCode, boolean liquidity) throws IllegalInputException, IllegalBordereauException, RoundException {
        // validate paymentCode
        if (!formatsUtil.isValidPaymentCode(paymentCode)) {
            throw new IllegalInputException("Invalid paymentCode");
        }

        // verify blindedBordereau never registered
        if (dbService.isBlindedBordereauRegistered(blindedBordereau)) {
            throw new IllegalBordereauException("blindedBordereau already registered");
        }

        // verify UTXO not banned
        if (blameService.isBannedUTXO(utxoHash, utxoIndex)) {
            log.warn("Rejecting banned UTXO: "+utxoHash+":"+utxoIndex);
            throw new IllegalBordereauException("Banned from service");
        }

        // verify PaymentCode not banned
        if (blameService.isBannedPaymentCode(paymentCode)) {
            log.warn("Rejecting banned paymentCode: "+paymentCode);
            throw new IllegalBordereauException("Banned from service");
        }

        // validate input
        long samouraiFees = (liquidity ? 0 : whirlpoolServerConfig.getSamouraiFees().getAmount()); // no samouraiFees for liquidities
        int inputMinConfirmations = whirlpoolServerConfig.getRegisterInput().getMinConfirmations();
        TxOutPoint txOutPoint = blockchainService.validateAndGetPremixInput(utxoHash, utxoIndex, pubkey, inputMinConfirmations, samouraiFees);

        // verify signature
        checkInputSignature(roundId, pubkey, signature);

        // prepare signedBordereau
        byte[] signedBordereauToReply = cryptoService.signBlindedOutput(blindedBordereau);

        // register input and send back signedBordereau
        roundService.registerInput(roundId, username, txOutPoint, pubkey, paymentCode, signedBordereauToReply, liquidity);

        // register blindedBordereau
        dbService.registerBlindedBordereau(blindedBordereau);
    }

    private void checkInputSignature(String roundId, byte[] pubkeyHex, String signature) throws IllegalInputException {
        if (log.isDebugEnabled()) {
            log.debug("Verifying signature: " + signature + "\n  for pubkey: " + Utils.HEX.encode(pubkeyHex) + "\n  for roundId: " + roundId);
        }

        // verify signature of 'roundId' for pubkey
        if (!cryptoService.verifyMessageSignature(pubkeyHex, roundId, signature)) {
            throw new IllegalInputException("Invalid signature");
        }
    }

}
