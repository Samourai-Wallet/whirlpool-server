package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.RpcOut;
import com.samourai.whirlpool.server.beans.RpcTransaction;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

@Service
public class BlockchainDataService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private BitcoinJSONRPCClient rpcClient;
    private Map<String,RpcTransaction> mockTransactions;

    public BlockchainDataService(BitcoinJSONRPCClient rpcClient) {
        this.rpcClient = rpcClient;
        this.mockTransactions = new HashMap<>();
    }

    protected RpcTransaction getRpcTransaction(String hash) {
        RpcTransaction rpcTransaction = mockTransactions.get(hash);
        if (rpcTransaction != null) {
            return rpcTransaction;
        }
        BitcoindRpcClient.RawTransaction rawTransaction = rpcClient.getRawTransaction(hash);
        return newRpcTransaction(rawTransaction);
    }

    private RpcTransaction newRpcTransaction(BitcoindRpcClient.RawTransaction rawTransaction) {
        RpcTransaction rpcTransaction = new RpcTransaction(rawTransaction.hash(), rawTransaction.confirmations());

        for (BitcoindRpcClient.RawTransaction.Out out : rawTransaction.vOut()) {
            RpcOut rpcOut = new RpcOut(out.n(), (long)(out.value()*100000000), Utils.HEX.decode(out.scriptPubKey().hex()), out.scriptPubKey().addresses());
            rpcTransaction.addRpcOut(rpcOut);
        }
        return rpcTransaction;
    }

    public void broadcastTransaction(Transaction tx) throws Exception {
        // TODO implement
    }

    public void __mock(RpcTransaction rpcTransaction) {
        this.mockTransactions.put(rpcTransaction.getHash(), rpcTransaction);
    }

    public void __reset() {
        this.mockTransactions = new HashMap<>();
    }

}
