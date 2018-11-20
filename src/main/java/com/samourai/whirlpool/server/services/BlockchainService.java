package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.server.beans.rpc.RpcOutWithTx;
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
  private Bech32UtilGeneric bech32Util;

  public BlockchainService(
      CryptoService cryptoService,
      BlockchainDataService blockchainDataService,
      Tx0Service tx0Service,
      WhirlpoolServerConfig whirlpoolServerConfig,
      Bech32UtilGeneric bech32Util) {
    this.cryptoService = cryptoService;
    this.blockchainDataService = blockchainDataService;
    this.tx0Service = tx0Service;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.bech32Util = bech32Util;
  }

  public RpcOutWithTx validateAndGetPremixInput(
      String utxoHash, long utxoIndex, boolean liquidity, boolean testMode)
      throws IllegalInputException {
    RpcOutWithTx rpcOutWithTx =
        blockchainDataService
            .getRpcOutWithTx(utxoHash, utxoIndex)
            .orElseThrow(
                () -> new IllegalInputException("UTXO not found: " + utxoHash + "-" + utxoIndex));

    // tx0 verification can be disabled in testMode
    boolean skipTx0Checks = whirlpoolServerConfig.isTestMode() && testMode;

    // verify input comes from a valid tx0 (or from a valid mix)
    if (!skipTx0Checks) {
      boolean isLiquidity = tx0Service.checkInput(rpcOutWithTx);
      if (!isLiquidity && liquidity) {
        throw new IllegalInputException("Input rejected: joined as liquidity but is a mustMix");
      }
      if (isLiquidity && !liquidity) {
        throw new IllegalInputException("Input rejected: joined as mustMix but is as a liquidity");
      }
    } else {
      log.warn("tx0 checks disabled by testMode");
    }
    return rpcOutWithTx;
  }
}
