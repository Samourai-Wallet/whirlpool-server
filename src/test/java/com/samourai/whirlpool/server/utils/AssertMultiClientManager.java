package com.samourai.whirlpool.server.utils;

import com.samourai.http.client.JavaHttpClient;
import com.samourai.stomp.client.JavaStompClient;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip47.rpc.impl.Bip47Util;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.*;
import com.samourai.whirlpool.client.utils.MultiClientListener;
import com.samourai.whirlpool.client.utils.MultiClientManager;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientImpl;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.InputPool;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.services.CryptoService;
import com.samourai.whirlpool.server.services.MixLimitsService;
import com.samourai.whirlpool.server.services.rpc.MockRpcClientServiceImpl;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssertMultiClientManager extends MultiClientManager {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private TestUtils testUtils;
  private CryptoService cryptoService;
  private MockRpcClientServiceImpl rpcClientService;
  private MixLimitsService mixLimitsService;
  private Bip47Util bip47Util;
  private int port;
  private boolean ssl;
  private boolean testMode;

  private Mix mix;
  int currentMix = 0;

  private TxOutPoint[] inputs;
  private ECKey[] inputKeys;
  private BIP47Wallet[] bip47Wallets;
  private int[] paymentCodeIndexs;

  public AssertMultiClientManager(
      int nbClients,
      Mix mix,
      TestUtils testUtils,
      CryptoService cryptoService,
      MockRpcClientServiceImpl rpcClientService,
      MixLimitsService mixLimitsService,
      Bip47Util bip47Util,
      int port) {
    this.mix = mix;
    this.testUtils = testUtils;
    this.cryptoService = cryptoService;
    this.rpcClientService = rpcClientService;
    this.mixLimitsService = mixLimitsService;
    this.bip47Util = bip47Util;
    this.port = port;
    this.ssl = true;
    this.testMode = false;

    inputs = new TxOutPoint[nbClients];
    inputKeys = new ECKey[nbClients];
    bip47Wallets = new BIP47Wallet[nbClients];
    paymentCodeIndexs = new int[nbClients];
  }

  private WhirlpoolClient createClient() {
    String server = "127.0.0.1:" + port;
    WhirlpoolClientConfig config =
        new WhirlpoolClientConfig(
            new JavaHttpClient(),
            new JavaStompClient(),
            server,
            cryptoService.getNetworkParameters());
    config.setTestMode(testMode);
    config.setSsl(ssl);
    return WhirlpoolClientImpl.newClient(config);
  }

  private int prepareClientWithMock(long inputBalance) throws Exception {
    SegwitAddress inputAddress = testUtils.createSegwitAddress();
    BIP47Wallet bip47Wallet = testUtils.generateWallet().getBip47Wallet();
    int paymentCodeIndex = 0;
    return prepareClientWithMock(
        inputAddress, bip47Wallet, paymentCodeIndex, null, null, null, inputBalance);
  }

  private int prepareClientWithMock(
      SegwitAddress inputAddress,
      BIP47Wallet bip47Wallet,
      int paymentCodeIndex,
      Integer nbConfirmations,
      String utxoHash,
      Integer utxoIndex,
      long inputBalance)
      throws Exception {
    // prepare input & output and mock input
    TxOutPoint utxo =
        rpcClientService.createAndMockTxOutPoint(
            inputAddress, inputBalance, nbConfirmations, utxoHash, utxoIndex);
    ECKey utxoKey = inputAddress.getECKey();

    return prepareClient(utxo, utxoKey, bip47Wallet, paymentCodeIndex);
  }

  private synchronized int prepareClient(
      TxOutPoint utxo, ECKey utxoKey, BIP47Wallet bip47Wallet, int paymentCodeIndex) {
    int i = clients.size();
    register(createClient(), currentMix);
    bip47Wallets[i] = bip47Wallet;
    paymentCodeIndexs[i] = paymentCodeIndex;
    inputs[i] = utxo;
    inputKeys[i] = utxoKey;
    return i;
  }

  private long computeInputBalanceMin(boolean liquidity) {
    long inputBalance =
        WhirlpoolProtocol.computeInputBalanceMin(
            mix.getPool().getDenomination(), liquidity, mix.getPool().getMinerFeeMin());
    return inputBalance;
  }

  public void connectWithMockOrFail(boolean liquidity, int mixs) {
    long inputBalance = computeInputBalanceMin(liquidity);
    connectWithMockOrFail(mixs, inputBalance);
  }

  public void connectWithMockOrFail(int mixs, long inputBalance) {
    try {
      connectWithMock(mixs, inputBalance);
    } catch (Exception e) {
      log.error("", e);
      Assert.assertTrue(false);
    }
  }

  public void connectWithMock(int mixs, long inputBalance) throws Exception {
    int i = prepareClientWithMock(inputBalance);
    whirlpool(i, mixs);
  }

  public void connectWithMock(
      int mixs,
      SegwitAddress inputAddress,
      BIP47Wallet bip47Wallet,
      int paymentCodeIndex,
      Integer nbConfirmations,
      String utxoHash,
      Integer utxoIndex,
      long inputBalance)
      throws Exception {
    int i =
        prepareClientWithMock(
            inputAddress,
            bip47Wallet,
            paymentCodeIndex,
            nbConfirmations,
            utxoHash,
            utxoIndex,
            inputBalance);
    whirlpool(i, mixs);
  }

  public void connect(
      int mixs, TxOutPoint utxo, ECKey utxoKey, BIP47Wallet bip47Wallet, int paymentCodeIndex) {
    int i = prepareClient(utxo, utxoKey, bip47Wallet, paymentCodeIndex);
    whirlpool(i, mixs);
  }

  private void whirlpool(int i, int mixs) {
    Pool pool = mix.getPool();
    WhirlpoolClient whirlpoolClient = clients.get(i);
    MultiClientListener listener = listeners.get(i);
    TxOutPoint input = inputs[i];
    ECKey ecKey = inputKeys[i];
    int paymentCodeIndex = 0;

    BIP47Wallet bip47Wallet = bip47Wallets[i];
    UtxoWithBalance utxo = new UtxoWithBalance(input.getHash(), input.getIndex(), input.getValue());
    IPremixHandler premixHandler = new PremixHandler(utxo, ecKey);
    IPostmixHandler postmixHandler = new PostmixHandler(bip47Wallet, paymentCodeIndex, bip47Util);

    MixParams mixParams =
        new MixParams(pool.getPoolId(), pool.getDenomination(), premixHandler, postmixHandler);

    whirlpoolClient.whirlpool(mixParams, mixs, listener);
  }

  private void waitRegisteredInputs(int nbInputsExpected) throws Exception {
    int MAX_WAITS = 5;
    int WAIT_DURATION = 4000;
    for (int i = 0; i < MAX_WAITS; i++) {
      String msg =
          "# ("
              + (i + 1)
              + "/"
              + MAX_WAITS
              + ") Waiting for registered inputs: "
              + mix.getNbInputs()
              + "/"
              + nbInputsExpected;
      if (mix.getNbInputs() != nbInputsExpected) {
        log.info(msg + " : waiting longer...");
        Thread.sleep(WAIT_DURATION);
      } else {
        log.info(msg + " : success");
        return;
      }
    }

    // debug on failure
    log.info(
        "# (LAST) Waiting for registered inputs: " + mix.getNbInputs() + " " + nbInputsExpected);
    Assert.assertEquals(nbInputsExpected, mix.getNbInputs());
  }

  public void waitLiquiditiesInPool(int nbLiquiditiesInPoolExpected) throws Exception {
    InputPool liquidityPool = mix.getPool().getLiquidityQueue();

    int MAX_WAITS = 5;
    int WAIT_DURATION = 4000;
    for (int i = 0; i < MAX_WAITS; i++) {
      String msg =
          "# ("
              + (i + 1)
              + "/"
              + MAX_WAITS
              + ") Waiting for liquidities in pool: "
              + liquidityPool.getSize()
              + " vs "
              + nbLiquiditiesInPoolExpected;
      if (liquidityPool.getSize() != nbLiquiditiesInPoolExpected) {
        log.info(msg + " : waiting longer...");
        Thread.sleep(WAIT_DURATION);
      } else {
        log.info(msg + " : success");
        return;
      }
    }

    // debug on failure
    log.info(
        "# (LAST) Waiting for liquidities in pool: "
            + liquidityPool.getSize()
            + " vs "
            + nbLiquiditiesInPoolExpected);
    Assert.assertEquals(nbLiquiditiesInPoolExpected, liquidityPool.getSize());
  }

  public void waitMixStatus(MixStatus mixStatusExpected) throws Exception {
    int MAX_WAITS = 5;
    int WAIT_DURATION = 4000;
    for (int i = 0; i < MAX_WAITS; i++) {
      String msg =
          "# ("
              + (i + 1)
              + "/"
              + MAX_WAITS
              + ") Waiting for mixStatus: "
              + mix.getMixStatus()
              + " vs "
              + mixStatusExpected;
      if (!mix.getMixStatus().equals(mixStatusExpected)) {
        log.info(msg + " : waiting longer...");
        Thread.sleep(WAIT_DURATION);
      } else {
        log.info(msg + " : success");
        return;
      }
    }

    log.info("# (LAST) Waiting for mixStatus: " + mix.getMixStatus() + " vs " + mixStatusExpected);
    Assert.assertEquals(mixStatusExpected, mix.getMixStatus());
  }

  public void setMixNext() {
    Mix nextMix = mix.getPool().getCurrentMix();
    Assert.assertNotEquals(mix, nextMix);
    this.mix = nextMix;
    this.currentMix++;
    log.info("============= NEW MIX DETECTED: " + nextMix.getMixId() + " =============");
  }

  public void assertMixStatusConfirmInput(int nbInputsExpected, boolean hasLiquidityExpected)
      throws Exception {
    // wait inputs to register
    waitRegisteredInputs(nbInputsExpected);

    InputPool liquidityPool = mix.getPool().getLiquidityQueue();
    System.out.println("=> mixStatus=" + mix.getMixStatus() + ", nbInputs=" + mix.getNbInputs());

    // all clients should have registered their outputs
    Assert.assertEquals(MixStatus.CONFIRM_INPUT, mix.getMixStatus());
    Assert.assertEquals(nbInputsExpected, mix.getNbInputs());
    Assert.assertEquals(hasLiquidityExpected, liquidityPool.hasInputs());
  }

  public void assertMixStatusSuccess(int nbAllRegisteredExpected, boolean hasLiquidityExpected)
      throws Exception {
    assertMixStatusSuccess(nbAllRegisteredExpected, hasLiquidityExpected, 1);
  }

  public void assertMixStatusSuccess(
      int nbAllRegisteredExpected, boolean hasLiquidityExpected, int numMix) throws Exception {
    // wait inputs to register
    waitRegisteredInputs(nbAllRegisteredExpected);

    Thread.sleep(2000);

    // mix automatically switches to REGISTER_OUTPUTS, then SIGNING, then SUCCESS
    waitMixStatus(MixStatus.SUCCESS);
    Assert.assertEquals(MixStatus.SUCCESS, mix.getMixStatus());
    Assert.assertEquals(nbAllRegisteredExpected, mix.getNbInputs());

    InputPool liquidityPool = mix.getPool().getLiquidityQueue();
    Assert.assertEquals(hasLiquidityExpected, liquidityPool.hasInputs());

    // all clients should have registered their outputs
    Assert.assertEquals(nbAllRegisteredExpected, mix.getReceiveAddresses().size());

    // all clients should have signed
    Assert.assertEquals(nbAllRegisteredExpected, mix.getNbSignatures());

    // all clients should be SUCCESS
    assertClientsSuccess(nbAllRegisteredExpected, numMix);
  }

  public void assertMixTx(String expectedTxHash, String expectedTxHex) {
    Transaction tx = mix.getTx();
    String txHash = tx.getHashAsString();
    String txHex = Utils.HEX.encode(tx.bitcoinSerialize());
    Assert.assertEquals(expectedTxHash, txHash);
    Assert.assertEquals(expectedTxHex, txHex);
  }

  private void assertClientsSuccess(int nbSuccessExpected, int numMix) {
    waitDone(numMix, nbSuccessExpected);
    Assert.assertTrue(getNbSuccess(numMix) == nbSuccessExpected);
  }

  public void nextTargetAnonymitySetAdjustment() throws Exception {
    int targetAnonymitySetExpected = mix.getTargetAnonymitySet() - 1;
    if (targetAnonymitySetExpected < mix.getPool().getMinAnonymitySet()) {
      throw new Exception("targetAnonymitySetExpected < minAnonymitySet");
    }

    log.info(
        "nextTargetAnonymitySetAdjustment: "
            + (targetAnonymitySetExpected + 1)
            + " -> "
            + targetAnonymitySetExpected);

    // simulate 9min58 elapsed... mix targetAnonymitySet should remain unchanged
    mixLimitsService.__simulateElapsedTime(mix, mix.getPool().getTimeoutAdjustAnonymitySet() - 2);
    Thread.sleep(1000);
    Assert.assertEquals(targetAnonymitySetExpected + 1, mix.getTargetAnonymitySet());

    // a few seconds more, mix targetAnonymitySet should be decreased
    waitMixTargetAnonymitySet(targetAnonymitySetExpected);
  }

  public void waitMixTargetAnonymitySet(int targetAnonymitySetExpected) throws Exception {
    int MAX_WAITS = 5;
    int WAIT_DURATION = 1000;
    for (int i = 0; i < MAX_WAITS; i++) {
      String msg =
          "# ("
              + (i + 1)
              + "/"
              + MAX_WAITS
              + ") Waiting for mixTargetAnonymitySet: "
              + mix.getTargetAnonymitySet()
              + " vs "
              + targetAnonymitySetExpected
              + " ("
              + mix.getMixStatus()
              + ")";
      if (mix.getTargetAnonymitySet() != targetAnonymitySetExpected
          && !MixStatus.SUCCESS.equals(mix.getMixStatus())) {
        log.info(msg + " : waiting longer...");
        Thread.sleep(WAIT_DURATION);
      } else {
        log.info(msg + " : success");
        return;
      }
    }

    log.info(
        "# (LAST) Waiting for mixTargetAnonymitySet: "
            + mix.getTargetAnonymitySet()
            + " vs "
            + targetAnonymitySetExpected);
    Assert.assertEquals(targetAnonymitySetExpected, mix.getTargetAnonymitySet());
  }

  public void exit() {
    for (WhirlpoolClient whirlpoolClient : clients) {
      if (whirlpoolClient != null) {
        whirlpoolClient.exit();
      }
    }
  }

  public void setTestMode(boolean testMode) {
    this.testMode = testMode;
  }

  public void setSsl(boolean ssl) {
    this.ssl = ssl;
  }
}
