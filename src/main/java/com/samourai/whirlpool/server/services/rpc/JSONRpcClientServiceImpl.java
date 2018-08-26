package com.samourai.whirlpool.server.services.rpc;

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
import java.net.URL;
import java.util.Optional;

@Service
@Profile("!" + Utils.PROFILE_TEST)
public class JSONRpcClientServiceImpl implements RpcClientService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private WhirlpoolServerConfig whirlpoolServerConfig;
    private BitcoinJSONRPCClient rpcClient;

    private static final String CHAIN_TESTNET = "test";
    private static final String CHAIN_MAINNET = "main";

    public JSONRpcClientServiceImpl(WhirlpoolServerConfig whirlpoolServerConfig) throws Exception {
        log.info("Instanciating JSONRpcClientServiceImpl");
        this.whirlpoolServerConfig = whirlpoolServerConfig;
        URL url = computeRpcClientUrl();
        this.rpcClient = new BitcoinJSONRPCClient(url);
    }

    @Override
    public boolean testConnectivity() {
        String nodeUrl = rpcClient.rpcURL.toString();
        log.info("Connecting to bitcoin node... url=" + nodeUrl);
        try {
            // verify node connectivity
            long blockHeight = rpcClient.getBlockCount();

            // verify node network
            String expectedChain = getRpcChain();
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

    @Override
    public Optional<RpcRawTransactionResponse> getRawTransaction(String txid) {
        BitcoindRpcClient.RawTransaction rawTx = rpcClient.getRawTransaction(txid);
        if (rawTx == null) {
            return Optional.empty();
        }
        RpcRawTransactionResponse rpcTxResponse = new RpcRawTransactionResponse(rawTx.hex(), rawTx.confirmations());
        return Optional.of(rpcTxResponse);
    }

    @Override
    public void broadcastTransaction(Transaction tx) throws Exception {
        String txid = tx.getHashAsString();
        if (whirlpoolServerConfig.getRpcClient().isMockTxBroadcast()) {
            log.warn("NOT broadcasting tx " + txid + "(server.rpc-client.mock-tx-broadcast=TRUE)");
            return;
        }

        try {
            log.info("Broadcasting tx " + txid);
            rpcClient.sendRawTransaction(org.bitcoinj.core.Utils.HEX.encode(tx.bitcoinSerialize()));
        } catch (Exception e) {
            log.error("Unable to broadcast tx " + txid, e);
            throw new MixException("Unable to broadcast tx");
        }
    }

    private String getRpcChain() {
        return whirlpoolServerConfig.isTestnet() ? CHAIN_TESTNET : CHAIN_MAINNET;
    }

    private URL computeRpcClientUrl() throws Exception {
        WhirlpoolServerConfig.RpcClientConfig config = whirlpoolServerConfig.getRpcClient();
        String rpcClientUrl = config.getProtocol() + "://" + config.getUser() + ":" + config.getPassword() + "@" + config.getHost() + ":" + config.getPort();
        try {
            return new URL(rpcClientUrl);
        }
        catch(Exception e) {
            throw new Exception("invalid rpcClientUrl: "+rpcClientUrl);
        }
    }
}
