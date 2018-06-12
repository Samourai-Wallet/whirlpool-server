package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.RpcIn;
import com.samourai.whirlpool.server.beans.RpcOut;
import com.samourai.whirlpool.server.beans.RpcTransaction;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
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
    private WhirlpoolServerConfig whirlpoolServerConfig;
    private Map<String,RpcTransaction> mockTransactions;

    public BlockchainDataService(BitcoinJSONRPCClient rpcClient, WhirlpoolServerConfig whirlpoolServerConfig) {
        this.rpcClient = rpcClient;
        this.whirlpoolServerConfig = whirlpoolServerConfig;
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

        for (BitcoindRpcClient.RawTransaction.In in : rawTransaction.vIn()) {
            BitcoindRpcClient.RawTransaction.Out out = in.getTransactionOutput();
            RpcOut fromOut = new RpcOut(out.n(), (long)(out.value()*100000000), Utils.HEX.decode(out.scriptPubKey().hex()), out.scriptPubKey().addresses());
            RpcIn rpcIn = new RpcIn(fromOut, rpcTransaction);
            rpcTransaction.addRpcIn(rpcIn);
        }
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

    public boolean testConnectivity() {
        String nodeUrl = whirlpoolServerConfig.getRpcClient().getHost()+":"+whirlpoolServerConfig.getRpcClient().getPort();
        log.info("Connecting to bitcoin node... url="+nodeUrl);
        try {
            // verify node connectivity
            long blockHeight = rpcClient.getBlockCount();

            // verify node network
            String expectedChain = getRpcChain(whirlpoolServerConfig.isTestnet());
            if (!rpcClient.getBlockChainInfo().chain().equals(expectedChain)) {
                log.error("Invalid chain for bitcoin node: url="+nodeUrl+", chain=" + rpcClient.getBlockChainInfo().chain() + ", expectedChain="+expectedChain);
                return false;
            }

            // verify blockHeight
            if (blockHeight <= 0) {
                log.error("Invalid blockHeight for bitcoin node: url="+nodeUrl+", chain="+rpcClient.getBlockChainInfo().chain()+", blockHeight=" + blockHeight);
                return false;
            }
            log.info("Connected to bitcoin node: url="+nodeUrl+", chain="+rpcClient.getBlockChainInfo().chain()+", blockHeight=" + blockHeight);
            return true;
        }
        catch(Exception e) {
            log.info("Error connecting to bitcoin node: url="+nodeUrl+", error=" + e.getMessage());
            return false;
        }
    }

    private String getRpcChain(boolean isTestnet) {
        return isTestnet ? "test" : "main";
    }

}
