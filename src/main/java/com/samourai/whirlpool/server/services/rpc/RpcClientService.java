package com.samourai.whirlpool.server.services.rpc;

import org.bitcoinj.core.Transaction;

import java.util.Optional;

public interface RpcClientService {
    boolean testConnectivity();
    Optional<RpcRawTransactionResponse> getRawTransaction(String txid);
    void broadcastTransaction(Transaction tx) throws Exception;
}
