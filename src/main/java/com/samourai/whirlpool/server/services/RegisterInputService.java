package com.samourai.whirlpool.server.services;

import com.samourai.wallet.util.MessageSignUtilGeneric;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.beans.rpc.ValidatedInput;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.MixException;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RegisterInputService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private PoolService poolService;
  private CryptoService cryptoService;
  private InputValidationService inputValidationService;
  private BlameService blameService;
  private MessageSignUtilGeneric messageSignUtil;

  @Autowired
  public RegisterInputService(
      PoolService poolService,
      CryptoService cryptoService,
      InputValidationService inputValidationService,
      BlameService blameService,
      MessageSignUtilGeneric messageSignUtil) {
    this.poolService = poolService;
    this.cryptoService = cryptoService;
    this.inputValidationService = inputValidationService;
    this.blameService = blameService;
    this.messageSignUtil = messageSignUtil;
  }

  public synchronized void registerInput(
      String poolId,
      String username,
      String signature,
      String utxoHash,
      long utxoIndex,
      boolean liquidity,
      boolean testMode)
      throws IllegalInputException, MixException {
    if (!cryptoService.isValidTxHash(utxoHash)) {
      throw new IllegalInputException("Invalid utxoHash");
    }
    if (utxoIndex < 0) {
      throw new IllegalInputException("Invalid utxoIndex");
    }

    // verify UTXO not banned
    if (blameService.isBannedUTXO(utxoHash, utxoIndex)) {
      log.warn("Rejecting banned UTXO: " + utxoHash + ":" + utxoIndex);
      throw new IllegalInputException("Banned from service");
    }

    try {
      // verify input is a valid mustMix or liquidity
      ValidatedInput validatedInput =
          inputValidationService.validate(utxoHash, utxoIndex, liquidity, testMode);

      // verify signature
      ECKey pubkey = checkInputSignature(validatedInput.getToAddress(), poolId, signature);

      // register input to pool
      TxOutPoint txOutPoint =
          new TxOutPoint(
              utxoHash, utxoIndex, validatedInput.getValue(), validatedInput.getConfirmations());
      poolService.registerInput(poolId, username, pubkey.getPubKey(), liquidity, txOutPoint, true);
    } catch (IllegalInputException e) {
      log.warn("Input rejected (" + utxoHash + ":" + utxoIndex + "): " + e.getMessage());
      throw e;
    }
  }

  private ECKey checkInputSignature(String toAddress, String message, String signature)
      throws IllegalInputException {
    if (log.isDebugEnabled()) {
      log.debug(
          "Verifying signature: "
              + signature
              + "\n  for address: "
              + toAddress
              + "\n  for message: "
              + message);
    }

    // verify signature of message for address
    if (!messageSignUtil.verifySignedMessage(
        toAddress, message, signature, cryptoService.getNetworkParameters())) {
      throw new IllegalInputException("Invalid signature");
    }

    ECKey pubkey = messageSignUtil.signedMessageToKey(message, signature);
    if (pubkey == null) {
      throw new IllegalInputException("Invalid signature");
    }
    return pubkey;
  }
}
