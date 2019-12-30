package com.samourai.whirlpool.server.utils;

import com.samourai.wallet.client.Bip84Wallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.cli.config.CliConfig;
import com.samourai.whirlpool.cli.services.CliTorClientService;
import com.samourai.whirlpool.cli.services.JavaHttpClientService;
import com.samourai.whirlpool.cli.services.JavaStompClientService;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.Bip84PostmixHandler;
import com.samourai.whirlpool.client.mix.handler.IPostmixHandler;
import com.samourai.whirlpool.client.mix.handler.IPremixHandler;
import com.samourai.whirlpool.client.mix.handler.PremixHandler;
import com.samourai.whirlpool.client.mix.handler.UtxoWithBalance;
import com.samourai.whirlpool.client.utils.MultiClientListener;
import com.samourai.whirlpool.client.utils.MultiClientManager;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientImpl;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.InputPool;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.services.BlockchainDataService;
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
  private BlockchainDataService blockchainDataService;
  private int port;

  private Mix mix;

  private TxOutPoint[] inputs;
  private ECKey[] inputKeys;
  private Bip84Wallet[] bip84Wallets;

  public AssertMultiClientManager(
      int nbClients,
      Mix mix,
      TestUtils testUtils,
      CryptoService cryptoService,
      MockRpcClientServiceImpl rpcClientService,
      MixLimitsService mixLimitsService,
      BlockchainDataService blockchainDataService,
      int port) {
    this.mix = mix;
    this.testUtils = testUtils;
    this.cryptoService = cryptoService;
    this.rpcClientService = rpcClientService;
    this.mixLimitsService = mixLimitsService;
    this.blockchainDataService = blockchainDataService;
    this.port = port;

    inputs = new TxOutPoint[nbClients];
    inputKeys = new ECKey[nbClients];
    bip84Wallets = new Bip84Wallet[nbClients];
  }

  private WhirlpoolClient createClient(CliConfig cliConfig) {
    String server = "http://127.0.0.1:" + port;
    CliTorClientService cliTorClientService = new CliTorClientService(new CliConfig());
    JavaHttpClientService httpClientService =
        new JavaHttpClientService(cliTorClientService, cliConfig);
    WhirlpoolClientConfig config =
        new WhirlpoolClientConfig(
            httpClientService,
            new JavaStompClientService(cliTorClientService, cliConfig, httpClientService),
            new MemoryWalletPersistHandler(),
            server,
            cryptoService.getNetworkParameters(),
            false);
    return WhirlpoolClientImpl.newClient(config);
  }

  private int prepareClientWithMock(long inputBalance, CliConfig cliConfig) throws Exception {
    SegwitAddress inputAddress = testUtils.generateSegwitAddress();
    Bip84Wallet bip84Wallet = testUtils.generateWallet().getBip84Wallet(0);
    return prepareClientWithMock(inputAddress, bip84Wallet, null, null, inputBalance, cliConfig);
  }

  private int prepareClientWithMock(
      SegwitAddress inputAddress,
      Bip84Wallet bip84Wallet,
      Integer nbConfirmations,
      Integer utxoIndex,
      long inputBalance,
      CliConfig cliConfig)
      throws Exception {

    if (utxoIndex == null) {
      utxoIndex = 0;
    }
    Integer nbOuts = utxoIndex + 1;

    // prepare input & output and mock input
    RpcTransaction rpcTransaction =
        rpcClientService.createAndMockTx(inputAddress, inputBalance, nbConfirmations, nbOuts);
    TxOutPoint utxo = blockchainDataService.getOutPoint(rpcTransaction, utxoIndex);
    ECKey utxoKey = inputAddress.getECKey();

    return prepareClient(utxo, utxoKey, bip84Wallet, cliConfig);
  }

  private synchronized int prepareClient(
      TxOutPoint utxo, ECKey utxoKey, Bip84Wallet bip84Wallet, CliConfig cliConfig) {
    int i = clients.size();
    register(createClient(cliConfig));
    bip84Wallets[i] = bip84Wallet;
    inputs[i] = utxo;
    inputKeys[i] = utxoKey;
    return i;
  }

  private long computePremixBalanceMin(boolean liquidity) {
    long premixBalanceMin = mix.getPool().computePremixBalanceMin(liquidity);
    return premixBalanceMin;
  }

  public void connectWithMockOrFail(boolean liquidity, CliConfig cliConfig) {
    long premixBalanceMin = computePremixBalanceMin(liquidity);
    connectWithMockOrFail(premixBalanceMin, cliConfig);
  }

  public void connectWithMockOrFail(long inputBalance, CliConfig cliConfig) {
    try {
      connectWithMock(inputBalance, cliConfig);
    } catch (Exception e) {
      log.error("", e);
      Assert.assertTrue(false);
    }
  }

  public void connectWithMock(long inputBalance, CliConfig cliConfig) throws Exception {
    int i = prepareClientWithMock(inputBalance, cliConfig);
    whirlpool(i);
  }

  public void connectWithMock(
      SegwitAddress inputAddress,
      Bip84Wallet bip84Wallet,
      Integer nbConfirmations,
      Integer utxoIndex,
      long inputBalance,
      CliConfig cliConfig)
      throws Exception {
    int i =
        prepareClientWithMock(
            inputAddress, bip84Wallet, nbConfirmations, utxoIndex, inputBalance, cliConfig);
    whirlpool(i);
  }

  public void connect(
      TxOutPoint utxo, ECKey utxoKey, Bip84Wallet bip84Wallet, CliConfig cliConfig) {
    int i = prepareClient(utxo, utxoKey, bip84Wallet, cliConfig);
    whirlpool(i);
  }

  private void whirlpool(int i) {
    Pool pool = mix.getPool();
    WhirlpoolClient whirlpoolClient = clients.get(i);
    MultiClientListener listener = listeners.get(i);
    TxOutPoint input = inputs[i];
    ECKey ecKey = inputKeys[i];

    Bip84Wallet bip84Wallet = bip84Wallets[i];
    UtxoWithBalance utxo = new UtxoWithBalance(input.getHash(), input.getIndex(), input.getValue());
    IPremixHandler premixHandler =
        new PremixHandler(utxo, ecKey, "userPreHash" + input.getHash() + input.getIndex());
    IPostmixHandler postmixHandler = new Bip84PostmixHandler(bip84Wallet, false);

    MixParams mixParams =
        new MixParams(pool.getPoolId(), pool.getDenomination(), premixHandler, postmixHandler);

    whirlpoolClient.whirlpool(mixParams, listener);
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
    assertClientsSuccess(nbAllRegisteredExpected);
  }

  public void assertMixTx(String expectedTxHash, String expectedTxHex) {
    Transaction tx = mix.getTx();
    String txHash = tx.getHashAsString();
    String txHex = Utils.HEX.encode(tx.bitcoinSerialize());
    Assert.assertEquals(expectedTxHash, txHash);
    Assert.assertEquals(expectedTxHex, txHex);
  }

  private void assertClientsSuccess(int nbSuccessExpected) {
    waitDone(nbSuccessExpected);
    Assert.assertTrue(getNbSuccess() == nbSuccessExpected);
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
        whirlpoolClient.stop(true);
      }
    }
  }
}
