package com.samourai.whirlpool.server.services;

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
public class InputValidationService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private BlockchainDataService blockchainDataService;
  private Tx0Service tx0Service;
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private DbService dbService;

  public InputValidationService(
      BlockchainDataService blockchainDataService,
      Tx0Service tx0Service,
      WhirlpoolServerConfig whirlpoolServerConfig,
      DbService dbService) {
    this.blockchainDataService = blockchainDataService;
    this.tx0Service = tx0Service;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.dbService = dbService;
  }

  public RpcOutWithTx validate(
      String utxoHash, long utxoIndex, boolean liquidity, boolean testMode)
      throws IllegalInputException {
    RpcOutWithTx rpcOutWithTx =
        blockchainDataService
            .getRpcOutWithTx(utxoHash, utxoIndex)
            .orElseThrow(
                () -> new IllegalInputException("UTXO not found: " + utxoHash + "-" + utxoIndex));

    // tx0 verification can be disabled in testMode
    boolean skipInputChecks = whirlpoolServerConfig.isTestMode() && testMode;

    // verify input comes from a valid tx0 (or from a valid mix)
    if (!skipInputChecks) {
      boolean isLiquidity = checkInput(rpcOutWithTx);
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

  protected boolean checkInput(RpcOutWithTx rpcOutWithTx) throws IllegalInputException {
    RpcOut rpcOut = rpcOutWithTx.getRpcOut();
    RpcTransaction tx = rpcOutWithTx.getTx();
    if (!rpcOut.getHash().equals(tx.getTxid())) {
      throw new IllegalInputException("Unexpected usage of checkInput: rpcOut.hash != tx.hash");
    }

    long inputValue = rpcOutWithTx.getRpcOut().getValue();
    boolean liquidity = doCheckInput(tx, inputValue);
    return liquidity;
  }

  protected boolean doCheckInput(RpcTransaction tx, long inputValue) throws IllegalInputException {
    // is it a tx0?
    Integer x = tx0Service.findSamouraiFeesIndice(tx);
    if (x != null) {
      // this is a tx0 => mustMix

      // check fees paid
      if (!tx0Service.isTx0FeesPaid(tx, x)) {
        throw new IllegalInputException(
            "Input rejected (invalid fee for tx0=" + tx.getTxid() + ", x=" + x + ")");
      }
      return false; // mustMix
    } else {
      // this is not a valid tx0 => liquidity coming from a previous whirlpool tx

      boolean isWhirlpoolTx = isWhirlpoolTx(tx, inputValue);
      if (!isWhirlpoolTx) {
        throw new IllegalInputException("Input rejected (not a premix or whirlpool input)");
      }
      return true; // liquidity
    }
  }

  protected boolean isWhirlpoolTx(RpcTransaction tx, long denomination) {
    return dbService.hasMixTxid(tx.getTxid(), denomination);
  }


}
