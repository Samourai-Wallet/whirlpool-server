package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.beans.rpc.RpcOut;
import com.samourai.whirlpool.server.beans.rpc.RpcOutWithTx;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BlockchainService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private CryptoService cryptoService;
  private BlockchainDataService blockchainDataService;
  private Tx0Service tx0Service;
  private WhirlpoolServerConfig whirlpoolServerConfig;

  public BlockchainService(
      CryptoService cryptoService,
      BlockchainDataService blockchainDataService,
      Tx0Service tx0Service,
      WhirlpoolServerConfig whirlpoolServerConfig) {
    this.cryptoService = cryptoService;
    this.blockchainDataService = blockchainDataService;
    this.tx0Service = tx0Service;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
  }

  public TxOutPoint validateAndGetPremixInput(
      String utxoHash, long utxoIndex, byte[] pubkeyHex, boolean liquidity, boolean testMode)
      throws IllegalInputException {
    RpcOutWithTx rpcOutWithTx =
        blockchainDataService
            .getRpcOutWithTx(utxoHash, utxoIndex)
            .orElseThrow(
                () -> new IllegalInputException("UTXO not found: " + utxoHash + "-" + utxoIndex));
    RpcOut rpcOut = rpcOutWithTx.getRpcOut();

    // verify pubkey: pubkey should control this utxo
    checkPubkey(rpcOut, pubkeyHex);

    // tx0 verification can be disabled in testMode
    boolean skipTx0Checks = whirlpoolServerConfig.isTestMode() && testMode;

    // verify input comes from a valid tx0 (or from a valid mix)
    if (!skipTx0Checks) {
      boolean isLiquidity = tx0Service.checkInput(rpcOutWithTx);
      if (!isLiquidity && liquidity) {
        throw new IllegalArgumentException(
            "Input rejected: not recognized as a liquidity (but as a mustMix)");
      }
      if (isLiquidity && !liquidity) {
        throw new IllegalInputException(
            "Input rejected: not recognized as a mustMix (but as a liquidity)");
      }
    } else {
      log.warn("tx0 checks disabled by testMode");
    }

    RpcTransaction tx = rpcOutWithTx.getTx();
    TxOutPoint txOutPoint =
        new TxOutPoint(utxoHash, utxoIndex, rpcOut.getValue(), tx.getConfirmations());

    return txOutPoint;
  }

  protected void checkPubkey(RpcOut rpcOut, byte[] pubkeyHex) throws IllegalInputException {
    String toAddressFromPubkey =
        new SegwitAddress(pubkeyHex, cryptoService.getNetworkParameters()).getBech32AsString();
    String toAddressFromUtxo = rpcOut.getToAddress();
    if (toAddressFromUtxo == null || !toAddressFromPubkey.equals(toAddressFromUtxo)) {
      throw new IllegalInputException("Invalid pubkey for UTXO");
    }
  }
}
