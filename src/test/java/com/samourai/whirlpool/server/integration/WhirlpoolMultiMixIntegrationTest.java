package com.samourai.whirlpool.server.integration;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.utils.AssertMultiClientManager;
import java.lang.invoke.MethodHandles;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class WhirlpoolMultiMixIntegrationTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.setTestMode(true);
  }

  @Test
  public void whirlpool_2clients_2mixs() throws Exception {
    final int NB_CLIENTS = 3;
    final int NB_CLIENTS_FIRST_MIX = 2;

    // MIX #1
    long denomination = 200000000;
    long feeValue = 10000000;
    long minerFeeMin = 100;
    long minerFeeMax = 10000;
    int mustMixMin = 1;
    int anonymitySetTarget = NB_CLIENTS_FIRST_MIX;
    int anonymitySetMin = 1;
    int anonymitySetMax = NB_CLIENTS_FIRST_MIX;
    long anonymitySetAdjustTimeout = 10 * 60; // 10 minutes
    long liquidityTimeout = 60;
    Mix mix =
        __nextMix(
            denomination,
            feeValue,
            minerFeeMin,
            minerFeeMax,
            mustMixMin,
            anonymitySetTarget,
            anonymitySetMin,
            anonymitySetMax,
            anonymitySetAdjustTimeout,
            liquidityTimeout);

    AssertMultiClientManager multiClientManager = multiClientManager(NB_CLIENTS, mix);

    // connect 2 clients
    log.info("# Connect 2 clients for first mix...");
    for (int i = 0; i < NB_CLIENTS_FIRST_MIX; i++) {
      taskExecutor.execute(() -> multiClientManager.connectWithMockOrFail(false));
    }

    // all clients should have registered their outputs and signed
    multiClientManager.assertMixStatusSuccess(NB_CLIENTS_FIRST_MIX, true);

    /*
    // MIX #2
    Thread.sleep(2000);
    multiClientManager.setMixNext();

    // the 2 clients from first mix are liquidities for second mix
    multiClientManager.waitLiquiditiesInPool(NB_CLIENTS_FIRST_MIX);

    log.info("# Connect 1 mustMix for second mix...");
    taskExecutor.execute(() -> multiClientManager.connectWithMockOrFail(false, 1));

    // we have 1 mustMix + 2 liquidities
    multiClientManager.assertMixStatusConfirmInput(1, true);

    multiClientManager.nextTargetAnonymitySetAdjustment(); // add liquidities
    multiClientManager.assertMixStatusSuccess(2, true, 2); // still one liquidity*/
  }
}
