package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.MixException;
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
  private DbService dbService;

  @Autowired
  public RegisterInputService(
      PoolService poolService,
      CryptoService cryptoService,
      BlockchainDataService blockchainDataService,
      InputValidationService inputValidationService,
      BlameService blameService,
      DbService dbService) {
    this.poolService = poolService;
    this.cryptoService = cryptoService;
    this.blockchainDataService = blockchainDataService;
    this.inputValidationService = inputValidationService;
    this.blameService = blameService;
    this.dbService = dbService;
  }

  public synchronized void registerInput(
      String poolId,
      String username,
      String signature,
      String utxoHash,
      long utxoIndex,
      boolean liquidity,
      boolean testMode,
      String ip)
      throws IllegalInputException, MixException {
    if (!cryptoService.isValidTxHash(utxoHash)) {
      throw new IllegalInputException("Invalid utxoHash");
    }
    if (utxoIndex < 0) {
      throw new IllegalInputException("Invalid utxoIndex");
    }

    // verify UTXO not banned
    if (blameService.isBannedUTXO(utxoHash, utxoIndex)) {
      log.warn("Rejecting banned UTXO: " + utxoHash + ":" + utxoIndex + ", ip=" + ip);
      throw new IllegalInputException("Banned from service. Contact us.");
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

      // check tx0Whitelist
      String txid = rpcTransaction.getTx().getHashAsString();
      if (!dbService.hasTx0Whitelist(txid)) {
        // verify input is a valid mustMix or liquidity
        Pool pool = poolService.getPool(poolId);
        boolean hasMixTxid = dbService.hasMixTxid(txid, txOutPoint.getValue());
        inputValidationService.validateProvenance(
            txOutPoint, rpcTransaction, liquidity, testMode, pool, hasMixTxid);
      } else {
        log.warn("tx0 check disabled by whitelist for txid=" + txid);
      }

      // register input to pool
      poolService.registerInput(poolId, username, liquidity, txOutPoint, true, ip);
    } catch (IllegalInputException e) {
      log.warn("Input rejected (" + utxoHash + ":" + utxoIndex + "): " + e.getMessage());
      throw e;
    }
  }
}
