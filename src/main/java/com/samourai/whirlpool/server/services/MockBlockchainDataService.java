package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.whirlpool.server.beans.RpcIn;
import com.samourai.whirlpool.server.beans.RpcOut;
import com.samourai.whirlpool.server.beans.RpcTransaction;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import org.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
public class MockBlockchainDataService extends BlockchainDataService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private CryptoService cryptoService;
    private Bech32Util bech32Util;
    private Map<String,RpcTransaction> mockTransactions;

    private static final int MOCK_TX_CONFIRMATIONS = 99;

    public MockBlockchainDataService(BitcoinJSONRPCClient rpcClient, CryptoService cryptoService, Bech32Util bech32Util, WhirlpoolServerConfig whirlpoolServerConfig) {
        super(rpcClient, whirlpoolServerConfig);
        this.cryptoService = cryptoService;
        this.bech32Util = bech32Util;
        this.mockTransactions = new HashMap<>();
    }

    @Override
    public RpcTransaction getRpcTransaction(String hash) {
        RpcTransaction rpcTransaction = mockTransactions.get(hash);
        if (rpcTransaction != null) {
            return rpcTransaction;
        }
        return super.getRpcTransaction(hash);
    }

    @Override
    public void broadcastTransaction(Transaction tx) throws Exception {
        // mock result TX to simulate broadcast
        log.warn("Not broadcasting tx " + tx.getHashAsString()+" (mock-tx-broadcast enabled by configuration)");
        RpcTransaction rpcTransaction = newRpcTransaction(tx, MOCK_TX_CONFIRMATIONS);
        mock(rpcTransaction);
    }

    public void mock(RpcTransaction rpcTransaction) {
        log.info("mock tx: " + rpcTransaction.getHash());
        this.mockTransactions.put(rpcTransaction.getHash(), rpcTransaction);
    }

    public void mock(Transaction tx, int nbConfirmations) throws Exception {
        RpcTransaction rpcTransaction = newRpcTransaction(tx, nbConfirmations);
        mock(rpcTransaction);
    }

    public void resetMock() {
        this.mockTransactions = new HashMap<>();
    }

    private RpcTransaction newRpcTransaction(Transaction tx, int nbConfirmations) throws Exception {
        NetworkParameters params = cryptoService.getNetworkParameters();

        RpcTransaction rpcTransaction = new RpcTransaction(tx.getHashAsString(), nbConfirmations);
        for (TransactionInput in : tx.getInputs()) {
            TransactionOutPoint outPoint = in.getOutpoint();
            RpcOut fromOut = new RpcOut(outPoint.getIndex(), outPoint.getValue().getValue(), null, null); // TODO toAdresses & scriptPubKey & manque hash de l'outpoint pour pouvoir en faire qqch à la verif des inputs en chaine?
            RpcIn rpcIn = new RpcIn(fromOut, rpcTransaction);
            rpcTransaction.addRpcIn(rpcIn);
        }
        for (TransactionOutput out : tx.getOutputs()) {
            String toAddressBech32 = bech32Util.getAddressFromScript(out.getScriptPubKey(), params);
            RpcOut rpcOut = new RpcOut(out.getIndex(), out.getValue().getValue(), out.getScriptPubKey().getProgram(), Arrays.asList(toAddressBech32));
            rpcTransaction.addRpcOut(rpcOut);
        }
        return rpcTransaction;
    }

}
