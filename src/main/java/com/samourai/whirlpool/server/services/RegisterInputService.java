package com.samourai.whirlpool.server.services;

import com.samourai.wallet.util.MessageSignUtilGeneric;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.beans.rpc.RpcOut;
import com.samourai.whirlpool.server.beans.rpc.RpcOutWithTx;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
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
  private BlockchainService blockchainService;
  private BlameService blameService;
  private MessageSignUtilGeneric messageSignUtil;

  @Autowired
  public RegisterInputService(
      PoolService poolService,
      CryptoService cryptoService,
      BlockchainService blockchainService,
      BlameService blameService,
      MessageSignUtilGeneric messageSignUtil) {
    this.poolService = poolService;
    this.cryptoService = cryptoService;
    this.blockchainService = blockchainService;
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
      RpcOutWithTx rpcOutWithTx =
          blockchainService.validateAndGetPremixInput(utxoHash, utxoIndex, liquidity, testMode);
      RpcOut rpcOut = rpcOutWithTx.getRpcOut();
      RpcTransaction rpcTx = rpcOutWithTx.getTx();

      // verify signature
      ECKey pubkey = checkInputSignature(poolId, rpcOut.getToAddress(), signature);

      TxOutPoint txOutPoint =
          new TxOutPoint(utxoHash, utxoIndex, rpcOut.getValue(), rpcTx.getConfirmations());

      // register input to pool
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
