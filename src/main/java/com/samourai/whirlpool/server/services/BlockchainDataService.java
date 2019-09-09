package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import com.samourai.whirlpool.server.services.rpc.RpcRawTransactionResponse;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BlockchainDataService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private CryptoService cryptoService;
  private RpcClientService rpcClientService;
  private Bech32UtilGeneric bech32Util;

  public BlockchainDataService(
      CryptoService cryptoService,
      RpcClientService rpcClientService,
      Bech32UtilGeneric bech32Util) {
    this.cryptoService = cryptoService;
    this.rpcClientService = rpcClientService;
    this.bech32Util = bech32Util;
  }

  protected Optional<RpcTransaction> getRpcTransaction(String txid) {
    if (log.isTraceEnabled()) {
      log.trace("RPC query: getRawTransaction " + txid);
    }
    Optional<RpcRawTransactionResponse> queryRawTxHex = rpcClientService.getRawTransaction(txid);
    if (!queryRawTxHex.isPresent()) {
      log.error("Tx not found: " + txid);
      return Optional.empty();
    }
    try {
      NetworkParameters params = cryptoService.getNetworkParameters();
      RpcTransaction rpcTx = new RpcTransaction(queryRawTxHex.get(), params);
      return Optional.of(rpcTx);
    } catch (Exception e) {
      log.error("Unable to parse RpcRawTransactionResponse", e);
      return Optional.empty();
    }
  }

  public TxOutPoint getOutPoint(RpcTransaction rpcTransaction, long utxoIndex)
      throws IllegalInputException {
    String utxoHash = rpcTransaction.getTx().getHashAsString();
    IllegalInputException notFoundException =
        new IllegalInputException("UTXO not found: " + utxoHash + "-" + utxoIndex);
    TransactionOutput txOutput = rpcTransaction.getTx().getOutput(utxoIndex);
    if (txOutput == null) {
      throw notFoundException;
    }

    long inputValue = txOutput.getValue().getValue();
    String toAddress =
        Utils.getToAddressBech32(txOutput, bech32Util, cryptoService.getNetworkParameters());
    TxOutPoint txOutPoint =
        new TxOutPoint(
            utxoHash,
            utxoIndex,
            inputValue,
            rpcTransaction.getConfirmations(),
            txOutput.getScriptBytes(),
            toAddress);
    return txOutPoint;
  }
}
