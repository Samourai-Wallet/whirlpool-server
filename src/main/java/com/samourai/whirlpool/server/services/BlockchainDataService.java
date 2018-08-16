package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.RpcIn;
import com.samourai.whirlpool.server.beans.RpcOut;
import com.samourai.whirlpool.server.beans.RpcTransaction;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.utils.Utils;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;

@Service
public class BlockchainDataService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private BitcoinJSONRPCClient rpcClient;
    private WhirlpoolServerConfig whirlpoolServerConfig;

    public BlockchainDataService(BitcoinJSONRPCClient rpcClient, WhirlpoolServerConfig whirlpoolServerConfig) {
        this.rpcClient = rpcClient;
        this.whirlpoolServerConfig = whirlpoolServerConfig;
    }

    protected RpcTransaction getRpcTransaction(String hash) {
        BitcoindRpcClient.RawTransaction rawTransaction = rpcClient.getRawTransaction(hash);
        return newRpcTransaction(rawTransaction);
    }

    private RpcTransaction newRpcTransaction(BitcoindRpcClient.RawTransaction rawTransaction) {
        RpcTransaction rpcTransaction = new RpcTransaction(rawTransaction.hash(), rawTransaction.confirmations());

        for (BitcoindRpcClient.RawTransaction.In in : rawTransaction.vIn()) {
            BitcoindRpcClient.RawTransaction.Out out = in.getTransactionOutput();
            long amount = computeValueSatoshis(out.value());
            RpcOut fromOut = new RpcOut(out.n(), amount, org.bitcoinj.core.Utils.HEX.decode(out.scriptPubKey().hex()), out.scriptPubKey().addresses());
            RpcIn rpcIn = new RpcIn(fromOut, rpcTransaction);
            rpcTransaction.addRpcIn(rpcIn);
        }
        for (BitcoindRpcClient.RawTransaction.Out out : rawTransaction.vOut()) {
            long amount = computeValueSatoshis(out.value());
            RpcOut rpcOut = new RpcOut(out.n(), amount, org.bitcoinj.core.Utils.HEX.decode(out.scriptPubKey().hex()), out.scriptPubKey().addresses());
            rpcTransaction.addRpcOut(rpcOut);
        }
        return rpcTransaction;
    }

    public void broadcastTransaction(Transaction tx) throws Exception {
        String txid = tx.getHashAsString();
        try {
            log.info("Broadcasting tx " + txid);
            rpcClient.sendRawTransaction(Utils.getRawTx(tx));
        }
        catch(Exception e) {
            log.error("Unable to broadcast tx " + txid, e);
            throw new MixException("Unable to broadcast tx");
        }
    }

    public boolean testConnectivity() {
        String nodeUrl = rpcClient.rpcURL.toString();
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

    private long computeValueSatoshis(BigDecimal valueBtc) {
        long amount = valueBtc.multiply(new BigDecimal(100000000)).setScale(0).longValueExact();
        return amount;
    }
}
