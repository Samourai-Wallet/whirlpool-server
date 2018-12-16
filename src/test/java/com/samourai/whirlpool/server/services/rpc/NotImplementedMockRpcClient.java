package com.samourai.whirlpool.server.services.rpc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import wf.bitcoin.javabitcoindrpcclient.BitcoinRPCException;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;
import wf.bitcoin.javabitcoindrpcclient.GenericRpcException;

public class NotImplementedMockRpcClient implements BitcoindRpcClient {

  @Override
  public String createRawTransaction(List<TxInput> list, List<TxOutput> list1)
      throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String dumpPrivKey(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String getAccount(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String getAccountAddress(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<String> getAddressesByAccount(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public BigDecimal getBalance() throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public BigDecimal getBalance(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public BigDecimal getBalance(String s, int i) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public Info getInfo() throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public MiningInfo getMiningInfo() throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public MultiSig createMultiSig(int i, List<String> list) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public NetworkInfo getNetworkInfo() throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public TxOutSetInfo getTxOutSetInfo() {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public WalletInfo getWalletInfo() {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public Block getBlock(int i) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public Block getBlock(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String getRawBlock(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String getBlockHash(int i) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public BlockChainInfo getBlockChainInfo() throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public int getBlockCount() throws GenericRpcException {
    return 0;
  }

  @Override
  public String getNewAddress() throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String getNewAddress(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String getNewAddress(String s, String s1) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<String> getRawMemPool() throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String getBestBlockHash() throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public RawTransaction getRawTransaction(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String getRawTransactionHex(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public BigDecimal getReceivedByAddress(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public BigDecimal getReceivedByAddress(String s, int i) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void importPrivKey(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void importPrivKey(String s, String s1) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void importPrivKey(String s, String s1, boolean b) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public Object importAddress(String s, String s1, boolean b) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public Map<String, Number> listAccounts() throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public Map<String, Number> listAccounts(int i) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public Map<String, Number> listAccounts(int i, boolean b) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<LockedUnspent> listLockUnspent() {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<ReceivedAddress> listReceivedByAddress() throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<ReceivedAddress> listReceivedByAddress(int i) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<ReceivedAddress> listReceivedByAddress(int i, boolean b) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public TransactionsSinceBlock listSinceBlock() throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public TransactionsSinceBlock listSinceBlock(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public TransactionsSinceBlock listSinceBlock(String s, int i) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<Transaction> listTransactions() throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<Transaction> listTransactions(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<Transaction> listTransactions(String s, int i) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<Transaction> listTransactions(String s, int i, int i1) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<Unspent> listUnspent() throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<Unspent> listUnspent(int i) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<Unspent> listUnspent(int i, int i1) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<Unspent> listUnspent(int i, int i1, String... strings) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public boolean lockUnspent(boolean b, String s, int i) throws GenericRpcException {
    return false;
  }

  @Override
  public boolean move(String s, String s1, BigDecimal bigDecimal) throws GenericRpcException {
    return false;
  }

  @Override
  public boolean move(String s, String s1, BigDecimal bigDecimal, String s2)
      throws GenericRpcException {
    return false;
  }

  @Override
  public boolean move(String s, String s1, BigDecimal bigDecimal, int i)
      throws GenericRpcException {
    return false;
  }

  @Override
  public boolean move(String s, String s1, BigDecimal bigDecimal, int i, String s2)
      throws GenericRpcException {
    return false;
  }

  @Override
  public String sendFrom(String s, String s1, BigDecimal bigDecimal) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String sendFrom(String s, String s1, BigDecimal bigDecimal, int i)
      throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String sendFrom(String s, String s1, BigDecimal bigDecimal, int i, String s2)
      throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String sendFrom(String s, String s1, BigDecimal bigDecimal, int i, String s2, String s3)
      throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String sendRawTransaction(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String sendToAddress(String s, BigDecimal bigDecimal) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String sendToAddress(String s, BigDecimal bigDecimal, String s1)
      throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String sendToAddress(String s, BigDecimal bigDecimal, String s1, String s2)
      throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String signRawTransaction(String s, List<? extends TxInput> list, List<String> list1)
      throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void setGenerate(boolean b) throws BitcoinRPCException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<String> generate(int i) throws BitcoinRPCException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<String> generate(int i, long l) throws BitcoinRPCException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<String> generateToAddress(int i, String s) throws BitcoinRPCException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public AddressValidationResult validateAddress(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void invalidateBlock(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void reconsiderBlock(String s) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<PeerInfoResult> getPeerInfo() {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void stop() {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String getRawChangeAddress() {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public long getConnectionCount() {
    return 0;
  }

  @Override
  public BigDecimal getUnconfirmedBalance() {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public BigDecimal getDifficulty() {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void ping() {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public DecodedScript decodeScript(String s) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public NetTotals getNetTotals() {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public boolean getGenerate() {
    return false;
  }

  @Override
  public BigDecimal getNetworkHashPs() {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public boolean setTxFee(BigDecimal bigDecimal) {
    return false;
  }

  @Override
  public void addNode(String s, String s1) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void backupWallet(String s) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String signMessage(String s, String s1) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void dumpWallet(String s) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void importWallet(String s) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void keyPoolRefill() {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public BigDecimal getReceivedByAccount(String s) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void encryptWallet(String s) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void walletPassPhrase(String s, long l) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public boolean verifyMessage(String s, String s1, String s2) {
    return false;
  }

  @Override
  public String addMultiSigAddress(int i, List<String> list) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public String addMultiSigAddress(int i, List<String> list, String s) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public boolean verifyChain() {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<NodeInfo> getAddedNodeInfo(boolean b, String s) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public void submitBlock(String s) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public Transaction getTransaction(String s) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public TxOut getTxOut(String s, long l) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public AddressBalance getAddressBalance(String s) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public List<AddressUtxo> getAddressUtxo(String s) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public SmartFeeResult estimateSmartFee(int i) {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public BigDecimal estimatePriority(int i) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }

  @Override
  public BigDecimal estimateFee(int i) throws GenericRpcException {
    throw new RuntimeException("mock not implemented");
  }
}
