package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.RpcIn;
import com.samourai.whirlpool.server.beans.RpcOut;
import com.samourai.whirlpool.server.beans.RpcTransaction;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

@Service
public class TestBlockchainDataService extends BlockchainDataService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Map<String,RpcTransaction> mockTransactions;

    public TestBlockchainDataService(BitcoinJSONRPCClient rpcClient, WhirlpoolServerConfig whirlpoolServerConfig) {
        super(rpcClient, whirlpoolServerConfig);
        this.mockTransactions = new HashMap<>();
    }

    @Override
    protected RpcTransaction getRpcTransaction(String hash) {
        RpcTransaction rpcTransaction = mockTransactions.get(hash);
        if (rpcTransaction != null) {
            return rpcTransaction;
        }
        return super.getRpcTransaction(hash);
    }

    @Override
    public void broadcastTransaction(Transaction tx) throws Exception {
        // mock result TX to simulate broadcast
        int confirmations = 3;
        RpcTransaction rpcTransaction = newRpcTransaction(tx, confirmations);
        mock(rpcTransaction);
    }

    private RpcTransaction newRpcTransaction(Transaction tx, int nbConfirmations) {
        //NetworkParameters params = cryptoService.getNetworkParameters();

        RpcTransaction rpcTransaction = new RpcTransaction(tx.getHashAsString(), nbConfirmations);
        for (TransactionInput in : tx.getInputs()) {
            TransactionOutPoint outPoint = in.getOutpoint();
            RpcOut fromOut = new RpcOut(outPoint.getIndex(), outPoint.getValue().getValue(), null, null); // TODO toAdresses & scriptPubKey & manque hash de l'outpoint pour pouvoir en faire qqch à la verif des inputs en chaine?
            RpcIn rpcIn = new RpcIn(fromOut, rpcTransaction);
            rpcTransaction.addRpcIn(rpcIn);
        }
        for (TransactionOutput out : tx.getOutputs()) {
            //String toAddressBech32 = new SegwitAddress(out.getScriptPubKey().getPubKey(), params).getBech32AsString();
            RpcOut rpcOut = new RpcOut(out.getIndex(), out.getValue().getValue(), null/*out.getScriptPubKey().getPubKey()*/, null/*Arrays.asList(toAddressBech32)*/); // TODO toAdresses & pubkey
            rpcTransaction.addRpcOut(rpcOut);
        }
        return rpcTransaction;
    }

    public void mock(RpcTransaction rpcTransaction) {
        log.info("mock tx: " + rpcTransaction.getHash());
        this.mockTransactions.put(rpcTransaction.getHash(), rpcTransaction);
    }

    public void resetMock() {
        this.mockTransactions = new HashMap<>();
    }

}
