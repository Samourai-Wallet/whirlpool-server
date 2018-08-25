package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.RpcIn;
import com.samourai.whirlpool.server.beans.RpcOutWithTx;
import com.samourai.whirlpool.server.beans.RpcOut;
import com.samourai.whirlpool.server.beans.RpcTransaction;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.utils.Utils;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

@Service
@Profile("!" + Utils.PROFILE_TEST)
public class BlockchainDataService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private BitcoinJSONRPCClient rpcClient;
    private WhirlpoolServerConfig whirlpoolServerConfig;

    public BlockchainDataService(BitcoinJSONRPCClient rpcClient, WhirlpoolServerConfig whirlpoolServerConfig) {
        this.rpcClient = rpcClient;
        this.whirlpoolServerConfig = whirlpoolServerConfig;
    }

    protected Optional<RpcTransaction> getRpcTransaction(String hash) {
        BitcoindRpcClient.RawTransaction rawTransaction = rpcClient.getRawTransaction(hash);
        if (rawTransaction == null) {
            log.error("Tx not found: " + hash);
            return Optional.empty();
        }
        return Optional.of(newRpcTransaction(rawTransaction));
    }

    protected Optional<RpcOutWithTx> getRpcOutWithTx(String hash, long index) {
        Optional<RpcTransaction> txResponse = getRpcTransaction(hash);
        if (!txResponse.isPresent()) {
            log.error("UTXO transaction not found: " + hash);
            return Optional.empty();
        }
        RpcTransaction tx = txResponse.get();

        Optional<RpcOut> rpcOutResponse = Utils.findTxOutput(tx, index);
        if (!rpcOutResponse.isPresent()) {
            log.error("UTXO not found: " + hash + "-" + index);
            return Optional.empty();
        }
        RpcOutWithTx rpcOutWithTx = new RpcOutWithTx(rpcOutResponse.get(), tx);
        return Optional.of(rpcOutWithTx);
    }

    private RpcTransaction newRpcTransaction(BitcoindRpcClient.RawTransaction rawTransaction) {
        RpcTransaction rpcTransaction = new RpcTransaction(rawTransaction.hash(), rawTransaction.confirmations());

        for (BitcoindRpcClient.RawTransaction.In in : rawTransaction.vIn()) {
            RpcIn rpcIn = newRpcIn(in);
            rpcTransaction.addRpcIn(rpcIn);
        }
        for (BitcoindRpcClient.RawTransaction.Out out : rawTransaction.vOut()) {
            RpcOut rpcOut = newRpcOut(out, rpcTransaction.getHash());
            rpcTransaction.addRpcOut(rpcOut);
        }
        return rpcTransaction;
    }

    protected RpcOut newRpcOut(BitcoindRpcClient.RawTransaction.Out out, String hash) {
        long amount = Utils.btcToSatoshis(out.value());
        RpcOut rpcOut = new RpcOut(hash, out.n(), amount, org.bitcoinj.core.Utils.HEX.decode(out.scriptPubKey().hex()), out.scriptPubKey().addresses());
        return rpcOut;
    }

    protected RpcIn newRpcIn(BitcoindRpcClient.RawTransaction.In in) {
        RpcIn rpcIn = new RpcIn(in.txid(), in.vout());
        return rpcIn;
    }

    public void broadcastTransaction(Transaction tx) throws Exception {
        String txid = tx.getHashAsString();
        if (whirlpoolServerConfig.getRpcClient().isMockTxBroadcast()) {
            log.warn("NOT broadcasting tx " + txid + "(server.rpc-client.mock-tx-broadcast=TRUE)");
            return;
        }

        try {
            log.info("Broadcasting tx " + txid);
            rpcClient.sendRawTransaction(Utils.getRawTx(tx));
        } catch (Exception e) {
            log.error("Unable to broadcast tx " + txid, e);
            throw new MixException("Unable to broadcast tx");
        }
    }

    public boolean testConnectivity() {
        String nodeUrl = rpcClient.rpcURL.toString();
        log.info("Connecting to bitcoin node... url=" + nodeUrl);
        try {
            // verify node connectivity
            long blockHeight = rpcClient.getBlockCount();

            // verify node network
            String expectedChain = getRpcChain(whirlpoolServerConfig.isTestnet());
            if (!rpcClient.getBlockChainInfo().chain().equals(expectedChain)) {
                log.error("Invalid chain for bitcoin node: url=" + nodeUrl + ", chain=" + rpcClient.getBlockChainInfo().chain() + ", expectedChain=" + expectedChain);
                return false;
            }

            // verify blockHeight
            if (blockHeight <= 0) {
                log.error("Invalid blockHeight for bitcoin node: url=" + nodeUrl + ", chain=" + rpcClient.getBlockChainInfo().chain() + ", blockHeight=" + blockHeight);
                return false;
            }
            log.info("Connected to bitcoin node: url=" + nodeUrl + ", chain=" + rpcClient.getBlockChainInfo().chain() + ", blockHeight=" + blockHeight);
            return true;
        } catch (Exception e) {
            log.info("Error connecting to bitcoin node: url=" + nodeUrl + ", error=" + e.getMessage());
            return false;
        }
    }

    private String getRpcChain(boolean isTestnet) {
        return isTestnet ? "test" : "main";
    }

    // public access for tools
    public Optional<RpcTransaction> __getRpcTransaction(String hash) {
        return getRpcTransaction(hash);
    }
}
