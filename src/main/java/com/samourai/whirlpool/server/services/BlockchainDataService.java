package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.services.rpc.RpcClientService;
import com.samourai.whirlpool.server.services.rpc.RpcRawTransactionResponse;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BlockchainDataService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private CryptoService cryptoService;
  private RpcClientService rpcClientService;

  public BlockchainDataService(CryptoService cryptoService, RpcClientService rpcClientService) {
    this.cryptoService = cryptoService;
    this.rpcClientService = rpcClientService;
  }

  protected Optional<RpcTransaction> getRpcTransaction(String txid) {
    if (log.isDebugEnabled()) {
      log.debug("RPC query: getRawTransaction " + txid);
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
}
