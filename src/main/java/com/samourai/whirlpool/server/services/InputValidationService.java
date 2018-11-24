package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.ValidatedInput;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InputValidationService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private BlockchainDataService blockchainDataService;
  private FeeValidationService feeValidationService;
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private Bech32UtilGeneric bech32Util;
  private CryptoService cryptoService;
  private DbService dbService;

  public InputValidationService(
      BlockchainDataService blockchainDataService,
      FeeValidationService feeValidationService,
      WhirlpoolServerConfig whirlpoolServerConfig,
      Bech32UtilGeneric bech32Util,
      CryptoService cryptoService,
      DbService dbService) {
    this.blockchainDataService = blockchainDataService;
    this.feeValidationService = feeValidationService;
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.bech32Util = bech32Util;
    this.cryptoService = cryptoService;
    this.dbService = dbService;
  }

  public ValidatedInput validate(
      String utxoHash, long utxoIndex, boolean liquidity, boolean testMode)
      throws IllegalInputException {
    IllegalInputException notFoundException =
        new IllegalInputException("UTXO not found: " + utxoHash + "-" + utxoIndex);
    RpcTransaction rpcTransaction =
        blockchainDataService.getRpcTransaction(utxoHash).orElseThrow(() -> notFoundException);

    Transaction tx = rpcTransaction.getTx();
    TransactionOutput txOutput = tx.getOutput(utxoIndex);
    if (txOutput == null) {
      throw notFoundException;
    }

    // tx0 verification can be disabled in testMode
    boolean skipInputChecks = whirlpoolServerConfig.isTestMode() && testMode;

    long inputValue = txOutput.getValue().getValue();

    // verify input comes from a valid tx0 (or from a valid mix)
    if (!skipInputChecks) {
      boolean isLiquidity = checkInput(tx, inputValue);
      if (!isLiquidity && liquidity) {
        throw new IllegalInputException("Input rejected: joined as liquidity but is a mustMix");
      }
      if (isLiquidity && !liquidity) {
        throw new IllegalInputException("Input rejected: joined as mustMix but is as a liquidity");
      }
    } else {
      log.warn("tx0 checks disabled by testMode");
    }

    String toAddress =
        Utils.getToAddressBech32(txOutput, bech32Util, cryptoService.getNetworkParameters());
    ValidatedInput validatedInput =
        new ValidatedInput(rpcTransaction.getConfirmations(), inputValue, toAddress);
    return validatedInput;
  }

  protected boolean checkInput(Transaction tx, long inputValue) throws IllegalInputException {
    // is it a tx0?
    Integer x = feeValidationService.findFeeIndice(tx);
    if (x != null) {
      // this is a tx0 => mustMix

      // check fees paid
      if (!feeValidationService.isTx0FeePaid(tx, x)) {
        throw new IllegalInputException(
            "Input rejected (invalid fee for tx0=" + tx.getHashAsString() + ", x=" + x + ")");
      }
      return false; // mustMix
    } else {
      // this is not a valid tx0 => liquidity coming from a previous whirlpool tx

      boolean isWhirlpoolTx = isWhirlpoolTx(tx.getHashAsString(), inputValue);
      if (!isWhirlpoolTx) {
        throw new IllegalInputException("Input rejected (not a premix or whirlpool input)");
      }
      return true; // liquidity
    }
  }

  protected boolean isWhirlpoolTx(String txid, long denomination) {
    return dbService.hasMixTxid(txid, denomination);
  }
}
