package com.samourai.whirlpool.server.integration;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.server.beans.InputPool;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.utils.AssertMultiClientManager;
import java.lang.invoke.MethodHandles;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class WhirlpoolMustMixWithLiquiditiesIntegrationTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Before
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.setTestMode(true);
  }

  private AssertMultiClientManager runMustmixWithLiquidities(
      int mustMixMin,
      int liquidityMin,
      int anonymitySet,
      int NB_MUSTMIX_CONNECTING,
      int NB_LIQUIDITIES_CONNECTING)
      throws Exception {
    final int NB_ALL_CONNECTING = NB_MUSTMIX_CONNECTING + NB_LIQUIDITIES_CONNECTING;

    // start mix
    long denomination = 200000000;
    long feeValue = 10000000;
    long minerFeeMin = 100;
    long minerFeeCap = 9500;
    long minerFeeMax = 10000;
    long minerFeeMix = 510;
    Mix mix =
        __nextMix(
            denomination,
            feeValue,
            minerFeeMin,
            minerFeeCap,
            minerFeeMax,
            minerFeeMix,
            mustMixMin,
            liquidityMin,
            anonymitySet);

    AssertMultiClientManager multiClientManager = multiClientManager(NB_ALL_CONNECTING, mix);

    InputPool liquidityPool = mix.getPool().getLiquidityQueue();
    Assert.assertFalse(liquidityPool.hasInputs());

    // connect liquidities first
    log.info("# Begin connecting " + NB_LIQUIDITIES_CONNECTING + " liquidities...");
    for (int i = 0; i < NB_LIQUIDITIES_CONNECTING; i++) {
      log.info("Connecting liquidity #" + (i + 1) + "/" + NB_LIQUIDITIES_CONNECTING);
      taskExecutor.execute(() -> multiClientManager.connectWithMockOrFail(true, cliConfig));
    }

    // liquidities should be placed on queue...
    multiClientManager.waitLiquiditiesInPool(NB_LIQUIDITIES_CONNECTING);
    boolean hasLiquidityExpected = (NB_LIQUIDITIES_CONNECTING > 0);
    multiClientManager.assertMixStatusConfirmInput(0, hasLiquidityExpected);

    // connect clients wanting to mix
    log.info("# Begin connecting " + NB_MUSTMIX_CONNECTING + " mustMix...");
    for (int i = NB_LIQUIDITIES_CONNECTING; i < NB_ALL_CONNECTING; i++) {
      log.info("Connecting mustMix #" + (i + 1) + "/" + NB_ALL_CONNECTING);
      final int clientIndice = i;
      taskExecutor.execute(() -> multiClientManager.connectWithMockOrFail(false, cliConfig));
    }

    return multiClientManager;
  }

  @Test
  public void whirlpool_5mustMix5liquidities() throws Exception {
    // mustMix + liquidities will immediately be registered, targetAnonymitySet won't be decreased
    final int NB_MUSTMIX_CONNECTING = 5;
    final int NB_LIQUIDITIES_CONNECTING = 5;
    int minMustMix = 5;
    int minLiquidity = 0;
    int anonymitySet = 5;

    final int NB_ALL_REGISTERED_EXPECTED = NB_MUSTMIX_CONNECTING + NB_LIQUIDITIES_CONNECTING;

    // TEST
    AssertMultiClientManager multiClientManager =
        runMustmixWithLiquidities(
            minMustMix,
            minLiquidity,
            anonymitySet,
            NB_MUSTMIX_CONNECTING,
            NB_LIQUIDITIES_CONNECTING);

    // VERIFY
    multiClientManager.assertMixStatusSuccess(NB_ALL_REGISTERED_EXPECTED, false);
  }

  @Test
  public void whirlpool_5mustMix5liquidities_cappedByMaxAnonymitySet() throws Exception {
    // mustMix + liquidities will immediately be registered, targetAnonymitySet won't be decreased
    final int NB_MUSTMIX_CONNECTING = 5;
    final int NB_LIQUIDITIES_CONNECTING = 5;
    int minMustMix = 5;
    int minLiquidity = 0;
    int anonymitySet = 5;

    final int NB_ALL_REGISTERED_EXPECTED = 8; // capped by maxAnonymitySet

    // TEST
    AssertMultiClientManager multiClientManager =
        runMustmixWithLiquidities(
            minMustMix,
            minLiquidity,
            anonymitySet,
            NB_MUSTMIX_CONNECTING,
            NB_LIQUIDITIES_CONNECTING);

    // VERIFY
    multiClientManager.assertMixStatusSuccess(
        NB_ALL_REGISTERED_EXPECTED, true); // still liquidities
  }

  @Test
  public void whirlpool_5mustmix_4liquidities() throws Exception {
    // mustMix + liquidities will be registered immediately
    final int NB_MUSTMIX_CONNECTING = 5;
    final int NB_LIQUIDITIES_CONNECTING = 4;
    int minMustMix = 2;
    int minLiquidity = 0;
    int anonymitySet = 5;

    final int NB_ALL_REGISTERED_EXPECTED = 9;

    // TEST
    AssertMultiClientManager multiClientManager =
        runMustmixWithLiquidities(
            minMustMix,
            minLiquidity,
            anonymitySet,
            NB_MUSTMIX_CONNECTING,
            NB_LIQUIDITIES_CONNECTING);

    // VERIFY
    multiClientManager.assertMixStatusSuccess(
        NB_ALL_REGISTERED_EXPECTED, false); // no more liquidity
  }

  @Test
  public void whirlpool_7mustMix7liquidities() throws Exception {
    // mustMix + liquidities will immediately be registered, targetAnonymitySet won't be decreased
    final int NB_MUSTMIX_CONNECTING = 7;
    final int NB_LIQUIDITIES_CONNECTING = 7;
    int minMustMix = 5;
    int minLiquidity = 0;
    int anonymitySet = 7;

    final int NB_ALL_REGISTERED_EXPECTED = NB_MUSTMIX_CONNECTING + NB_LIQUIDITIES_CONNECTING;

    // TEST
    AssertMultiClientManager multiClientManager =
        runMustmixWithLiquidities(
            minMustMix,
            minLiquidity,
            anonymitySet,
            NB_MUSTMIX_CONNECTING,
            NB_LIQUIDITIES_CONNECTING);

    // VERIFY
    multiClientManager.assertMixStatusSuccess(NB_ALL_REGISTERED_EXPECTED, false);
  }

  @Test
  public void whirlpool_15mustMix20liquidities() throws Exception {
    // mustMix + liquidities will immediately be registered, targetAnonymitySet won't be decreased
    final int NB_MUSTMIX_CONNECTING = 15;
    final int NB_LIQUIDITIES_CONNECTING = 20;
    int minMustMix = 15;
    int minLiquidity = 0;
    int anonymitySet = 7;

    final int NB_ALL_REGISTERED_EXPECTED = NB_MUSTMIX_CONNECTING + NB_LIQUIDITIES_CONNECTING;

    // TEST
    AssertMultiClientManager multiClientManager =
        runMustmixWithLiquidities(
            minMustMix,
            minLiquidity,
            anonymitySet,
            NB_MUSTMIX_CONNECTING,
            NB_LIQUIDITIES_CONNECTING);

    // VERIFY
    multiClientManager.assertMixStatusSuccess(NB_ALL_REGISTERED_EXPECTED, false);
  }

  @Test
  public void whirlpool_fail_notEnoughMustMix() throws Exception {
    // will miss 1 mustMix
    final int NB_MUSTMIX_CONNECTING = 4;
    final int NB_LIQUIDITIES_CONNECTING = 5;
    int minMustMix = 5;
    int minLiquidity = 0;
    int anonymitySet = 5;

    final int NB_ALL_REGISTERED_EXPECTED = 4;

    // TEST
    AssertMultiClientManager multiClientManager =
        runMustmixWithLiquidities(
            minMustMix,
            minLiquidity,
            anonymitySet,
            NB_MUSTMIX_CONNECTING,
            NB_LIQUIDITIES_CONNECTING);

    // VERIFY
    multiClientManager.assertMixStatusConfirmInput(
        NB_ALL_REGISTERED_EXPECTED, true); // liquidities not registered yet
  }

  @Test
  public void whirlpool_fail_notEnoughMustMixBis() throws Exception {
    // will miss 1 mustMix
    final int NB_MUSTMIX_CONNECTING = 5;
    final int NB_LIQUIDITIES_CONNECTING = 5;
    int minMustMix = 6;
    int minLiquidity = 0;
    int anonymitySet = 5;

    int NB_MUSTMIX_EXPECTED = 5;

    // TEST
    AssertMultiClientManager multiClientManager =
        runMustmixWithLiquidities(
            minMustMix,
            minLiquidity,
            anonymitySet,
            NB_MUSTMIX_CONNECTING,
            NB_LIQUIDITIES_CONNECTING);

    // VERIFY
    multiClientManager.assertMixStatusConfirmInput(NB_MUSTMIX_EXPECTED, true);
  }

  @Test
  public void whirlpool_success_noliquidity() throws Exception {
    // will miss 1 mustMix
    final int NB_MUSTMIX_CONNECTING = 5;
    final int NB_LIQUIDITIES_CONNECTING = 0;
    int minMustMix = 5;
    int minLiquidity = 0;
    int anonymitySet = 5;

    int NB_MUSTMIX_EXPECTED = 5;
    final int NB_ALL_REGISTERED_EXPECTED = 5;

    // TEST
    AssertMultiClientManager multiClientManager =
        runMustmixWithLiquidities(
            minMustMix,
            minLiquidity,
            anonymitySet,
            NB_MUSTMIX_CONNECTING,
            NB_LIQUIDITIES_CONNECTING);

    // VERIFY
    multiClientManager.assertMixStatusConfirmInput(NB_MUSTMIX_EXPECTED, false);

    // liquidities should have been registered
    multiClientManager.assertMixStatusSuccess(NB_ALL_REGISTERED_EXPECTED, false);
  }
}
