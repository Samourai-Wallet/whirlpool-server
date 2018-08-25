package com.samourai.whirlpool.server.services;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.whirlpool.server.beans.RpcOut;
import com.samourai.whirlpool.server.beans.RpcOutWithTx;
import com.samourai.whirlpool.server.beans.RpcTransaction;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.utils.TestUtils;
import com.samourai.whirlpool.server.utils.Utils;
import org.aspectj.util.FileUtil;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.*;

@Service
@Profile(Utils.PROFILE_TEST)
public class MockBlockchainDataService extends BlockchainDataService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private CryptoService cryptoService;
    private Bech32Util bech32Util;
    private TestUtils testUtils;
    private Map<String,RpcTransaction> mockTransactions;

    private static final int MOCK_TX_CONFIRMATIONS = 99;

    public MockBlockchainDataService(CryptoService cryptoService, Bech32Util bech32Util, WhirlpoolServerConfig whirlpoolServerConfig, TestUtils testUtils) {
        super(null, whirlpoolServerConfig);
        this.cryptoService = cryptoService;
        this.bech32Util = bech32Util;
        this.testUtils = testUtils;

        this.mockTransactions = new HashMap<>();
    }

    @Override
    public boolean testConnectivity() {
        log.info("NOT connecting to bitcoin node.");
        return true;
    }

    @Override
    protected Optional<RpcTransaction> getRpcTransaction(String txid) {
        // load mock from map
        RpcTransaction rpcTransaction = mockTransactions.get(txid);
        if (rpcTransaction == null) {
            // load mock from mock file
            rpcTransaction = loadMock(txid);
            if (rpcTransaction == null) {
                log.error("mocked tx not found: " + txid);
                return Optional.empty();
            }
        }
        return Optional.of(rpcTransaction);
    }

    @Override
    protected Optional<RpcOutWithTx> getRpcOutWithTx(String hash, long index) {
        return super.getRpcOutWithTx(hash, index);
    }

    @Override
    public void broadcastTransaction(Transaction tx) throws Exception {
        // mock result TX to simulate broadcast
        log.warn("Not broadcasting tx " + tx.getHashAsString()+" (mock-tx-broadcast enabled by configuration)");
        RpcTransaction rpcTransaction = newRpcTransaction(tx, MOCK_TX_CONFIRMATIONS);
        mock(rpcTransaction);
    }

    public void mock(RpcTransaction rpcTransaction) {
        log.info("mock tx: " + rpcTransaction.getTxid());
        this.mockTransactions.put(rpcTransaction.getTxid(), rpcTransaction);
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
        /*for (TransactionInput in : tx.getInputs()) {
            TransactionOutPoint outPoint = in.getOutpoint();
            RpcOut fromOut = new RpcOut(outPoint.getIndex(), outPoint.getValue().getValue(), null, null); // TODO toAdresses & scriptPubKey & manque hash de l'outpoint pour pouvoir en faire qqch à la verif des inputs en chaine?
            RpcIn rpcIn = new RpcIn(fromOut, rpcTransaction);
            rpcTransaction.addRpcIn(rpcIn);
        }
        for (TransactionOutput out : tx.getOutputs()) {
            String toAddressBech32 = bech32Util.getAddressFromScript(out.getScriptPubKey(), params);
            RpcOut rpcOut = new RpcOut(out.getIndex(), out.getValue().getValue(), out.getScriptPubKey().getProgram(), Arrays.asList(toAddressBech32));
            rpcTransaction.addRpcOut(rpcOut);
        }*/
        //TODO !!!!!!!!!!!!!!!!!!!!!
        return rpcTransaction;
    }

    // -----------

    public TxOutPoint createAndMockTxOutPoint(SegwitAddress address, long amount) throws Exception {
        return createAndMockTxOutPoint(address, amount, null, null, null);
    }

    public TxOutPoint createAndMockTxOutPoint(SegwitAddress address, long amount, int nbConfirmations) throws Exception {
        return createAndMockTxOutPoint(address, amount, nbConfirmations, null, null);
    }

    public TxOutPoint createAndMockTxOutPoint(SegwitAddress address, long amount, Integer nbConfirmations, String utxoHash, Integer utxoIndex) throws Exception{
        NetworkParameters params = cryptoService.getNetworkParameters();
        // generate transaction with bitcoinj
        Transaction transaction = new Transaction(params);

        if (nbConfirmations == null) {
            nbConfirmations = 1000;
        }

        if (utxoHash != null) {
            transaction.setHash(Sha256Hash.wrap(Hex.decode(utxoHash)));
        }

        if (utxoIndex != null) {
            for (int i=0; i<utxoIndex; i++) {
                transaction.addOutput(Coin.valueOf(amount), testUtils.createSegwitAddress().getAddress());
            }
        }
        String addressBech32 = address.getBech32AsString();
        TransactionOutput transactionOutput = bech32Util.getTransactionOutput(addressBech32, amount, params);
        transaction.addOutput(transactionOutput);
        if (utxoIndex == null) {
            utxoIndex = transactionOutput.getIndex();
        }
        else {
            Assert.assertEquals((long)utxoIndex, transactionOutput.getIndex());
        }

        // mock tx
        mock(transaction, nbConfirmations);

        // verify mock
        RpcOutWithTx rpcOutWithTx = getRpcOutWithTx(transaction.getHashAsString(), utxoIndex).orElseThrow(() -> new NoSuchElementException());
        RpcOut rpcOut = rpcOutWithTx.getRpcOut();
        Assert.assertEquals(addressBech32, bech32Util.getAddressFromScript(new Script(rpcOut.getScriptPubKey()), params));

        TxOutPoint txOutPoint = new TxOutPoint(rpcOut.getHash(), rpcOut.getIndex(), amount);
        return txOutPoint;
    }

    public RpcTransaction loadMock(String txid) {
        String jsonFile = testUtils.getMockFileName(txid);
        try {
            log.info("loading mock: " + jsonFile);
            String json = FileUtil.readAsString(new File(jsonFile));
            RpcTransaction tx = Utils.fromJsonString(json, RpcTransaction.class);
            return tx;
        } catch(Exception e) {
            log.info("mock not found: " + jsonFile);
            return null;
        }
    }

}
