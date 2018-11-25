package com.samourai.whirlpool.server.services;

import com.samourai.wallet.util.MessageSignUtilGeneric;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RegisterInputService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private PoolService poolService;
  private CryptoService cryptoService;
  private BlockchainDataService blockchainDataService;
  private InputValidationService inputValidationService;
  private BlameService blameService;

  @Autowired
  public RegisterInputService(
      PoolService poolService,
      CryptoService cryptoService,
      BlockchainDataService blockchainDataService,
      InputValidationService inputValidationService,
      BlameService blameService) {
    this.poolService = poolService;
    this.cryptoService = cryptoService;
    this.blockchainDataService = blockchainDataService;
    this.inputValidationService = inputValidationService;
    this.blameService = blameService;
  }

  public synchronized void registerInput(
      String poolId,
      String username,
      String signature,
      String utxoHash,
      long utxoIndex,
      boolean liquidity,
      boolean testMode)
      throws IllegalInputException {
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
      // fetch outPoint
      IllegalInputException notFoundException =
          new IllegalInputException("UTXO not found: " + utxoHash + "-" + utxoIndex);
      RpcTransaction rpcTransaction =
          blockchainDataService.getRpcTransaction(utxoHash).orElseThrow(() -> notFoundException);
      TxOutPoint txOutPoint = blockchainDataService.getOutPoint(rpcTransaction, utxoIndex);

      // verify signature
      inputValidationService.validateSignature(txOutPoint, poolId, signature);

      // verify input is a valid mustMix or liquidity
      inputValidationService.validateProvenance(txOutPoint, rpcTransaction.getTx(), liquidity, testMode);

      // register input to pool
      poolService.registerInput(poolId, username, liquidity, txOutPoint, true);
    } catch (IllegalInputException e) {
      log.warn("Input rejected (" + utxoHash + ":" + utxoIndex + "): " + e.getMessage());
      throw e;
    }
  }
}
