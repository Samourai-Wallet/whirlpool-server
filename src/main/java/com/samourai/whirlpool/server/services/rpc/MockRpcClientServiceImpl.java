package com.samourai.whirlpool.server.services.rpc;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.services.CryptoService;
import com.samourai.whirlpool.server.utils.Utils;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.aspectj.util.FileUtil;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import wf.bitcoin.javabitcoindrpcclient.GenericRpcException;

@Service
@Profile(Utils.PROFILE_TEST)
public class MockRpcClientServiceImpl implements RpcClientService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private NetworkParameters params;
  private Bech32UtilGeneric bech32Util;

  private Map<String, RpcRawTransactionResponse> mockTransactions;
  private Map<String, Boolean> mockSpentOutputs;

  public static final int MOCK_TX_CONFIRMATIONS = 99;
  private static final long MOCK_TX_TIME = 900000;

  public MockRpcClientServiceImpl(CryptoService cryptoService, Bech32UtilGeneric bech32Util) {
    log.info("Instanciating MockRpcClientServiceImpl");
    this.params = cryptoService.getNetworkParameters();
    this.bech32Util = bech32Util;

    resetMock();
  }

  @Override
  public boolean testConnectivity() {
    log.info("NOT connecting to bitcoin node.");
    return true;
  }

  @Override
  public void broadcastTransaction(Transaction tx) {
    // mock result TX to simulate broadcast
    String txid = tx.getHashAsString();
    log.warn("NOT broadcasting tx (mock-tx-broadcast=1): " + txid);
    mock(txid, org.bitcoinj.core.Utils.HEX.encode(tx.bitcoinSerialize()), MOCK_TX_CONFIRMATIONS);
  }

  @Override
  public Optional<RpcRawTransactionResponse> getRawTransaction(String txid)
      throws GenericRpcException {
    // load mock from map
    RpcRawTransactionResponse rawTxResponse = mockTransactions.get(txid);
    if (rawTxResponse != null) {
      log.info("mocked tx found in mockTransactions{}: " + txid);
      return Optional.of(rawTxResponse);
    }

    // load mock from mock file
    Optional<String> rpcTxHex = loadMockTx(txid);
    if (!rpcTxHex.isPresent()) {
      return Optional.empty();
    }
    RpcRawTransactionResponse rpcTxResponse =
        new RpcRawTransactionResponse(rpcTxHex.get(), MOCK_TX_CONFIRMATIONS, MOCK_TX_TIME);
    return Optional.of(rpcTxResponse);
  }

  @Override
  public boolean isTxOutUnspent(String txid, long index) {
    return !mockSpentOutputs.containsKey(txid+":"+index);
  }

  public void mock(String txid, String rawTxHex, int confirmations) {
    log.info("mock tx: " + txid);
    RpcRawTransactionResponse rawTxResponse =
        new RpcRawTransactionResponse(rawTxHex, confirmations, MOCK_TX_TIME);
    mockTransactions.put(txid, rawTxResponse);
  }

  public void mockSpentOutput(String txid, long index) {
    mockSpentOutputs.put(txid+":"+index, true);
  }

  public void resetMock() {
    mockTransactions = new HashMap<>();
    mockSpentOutputs = new HashMap<>();
  }

  // ------------

  public RpcTransaction createAndMockTx(
      SegwitAddress address, long amount, Integer nbConfirmations, int nbOuts) throws Exception {
    // generate transaction with bitcoinj
    Transaction transaction = new Transaction(params);

    if (nbConfirmations == null) {
      nbConfirmations = MOCK_TX_CONFIRMATIONS;
    }

    // add outputs
    String addressBech32 = address.getBech32AsString();
    for (int i = 0; i < nbOuts; i++) {
      TransactionOutput transactionOutput =
          bech32Util.getTransactionOutput(addressBech32, amount, params);
      transaction.addOutput(transactionOutput);
      Assert.isTrue(i == transactionOutput.getIndex(), "assert failed");
    }
    int utxoIndex = nbOuts - 1;

    // add coinbase input
    int txCounter = 1;
    TransactionInput transactionInput =
        new TransactionInput(
            params, transaction, new byte[] {(byte) txCounter, (byte) (txCounter++ >> 8)});
    transaction.addInput(transactionInput);

    // mock tx
    String txid = transaction.getHashAsString();
    mock(txid, org.bitcoinj.core.Utils.HEX.encode(transaction.bitcoinSerialize()), nbConfirmations);
    log.info("mocked txid=" + txid + ":\n" + transaction.toString());

    // verify mock
    RpcRawTransactionResponse rawTxResponse = getRawTransaction(txid).get();
    RpcTransaction rpcTransaction = new RpcTransaction(rawTxResponse, params);

    TransactionOutput txOutput = rpcTransaction.getTx().getOutput(utxoIndex);
    Assert.isTrue(
        addressBech32.equals(bech32Util.getAddressFromScript(txOutput.getScriptPubKey(), params)),
        "assert failed");

    return rpcTransaction;
  }

  private Optional<String> loadMockTx(String txid) {
    String mockFile = getMockFileName(txid);
    try {
      log.info("reading mock: " + mockFile);
      String rawTx = FileUtil.readAsString(new File(mockFile));
      return Optional.of(rawTx);
    } catch (Exception e) {
      log.info("mock not found: " + mockFile);
      return Optional.empty();
    }
  }

  private String getMockFileName(String txid) {
    return "./src/test/resources/mocks/" + txid + ".txt";
  }

  public void writeMockRpc(String txid, String rawTxHex) throws Exception {
    String fileName = getMockFileName(txid);
    System.out.println("writing " + fileName + ": " + rawTxHex);
    Files.write(Paths.get(fileName), rawTxHex.getBytes(), StandardOpenOption.CREATE);
  }
}
