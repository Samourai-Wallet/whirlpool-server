package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class MixServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void isRegisterInputReady_noLiquidity() throws Exception {
    MixService spyMixService = Mockito.spy(mixService);
    long denomination = 200000000;
    long feeValue = 10000000;
    long minerFeeMin = 100;
    long minerFeeCap = 9500;
    long minerFeeMax = 10000;
    int mustMixMin = 1;
    int liquidityMin = 0;
    int anonymitySetTarget = 2;
    int anonymitySetMin = 2;
    int anonymitySetMax = 2;
    long anonymitySetAdjustTimeout = 10 * 60;
    long liquidityTimeout = 60;
    Mix mix =
        __nextMix(
            denomination,
            feeValue,
            minerFeeMin,
            minerFeeCap,
            minerFeeMax,
            mustMixMin,
            liquidityMin,
            anonymitySetTarget,
            anonymitySetMin,
            anonymitySetMax,
            anonymitySetAdjustTimeout,
            liquidityTimeout);

    // 0 mustMix => false
    Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

    // 1 mustMix => false
    mix.registerInput(
        new ConfirmedInput(
            new RegisteredInput("mustMix1", false, generateOutPoint(), "127.0.0.1"), null));
    Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

    // 2 mustMix => true
    mix.registerInput(
        new ConfirmedInput(
            new RegisteredInput("mustMix2", false, generateOutPoint(), "127.0.0.1"), null));
    Assert.assertTrue(spyMixService.isRegisterInputReady(mix));
  }

  @Test
  public void isRegisterInputReady_withLiquidityBefore() throws Exception {
    MixService spyMixService = Mockito.spy(mixService);
    long denomination = 200000000;
    long feeValue = 10000000;
    long minerFeeMin = 100;
    long minerFeeCap = 9500;
    long minerFeeMax = 10000;
    int mustMixMin = 1;
    int liquidityMin = 0;
    int anonymitySetTarget = 2;
    int anonymitySetMin = 2;
    int anonymitySetMax = 2;
    long anonymitySetAdjustTimeout = 10 * 60;
    long liquidityTimeout = 60;
    Mix mix =
        __nextMix(
            denomination,
            feeValue,
            minerFeeMin,
            minerFeeCap,
            minerFeeMax,
            mustMixMin,
            liquidityMin,
            anonymitySetTarget,
            anonymitySetMin,
            anonymitySetMax,
            anonymitySetAdjustTimeout,
            liquidityTimeout);

    // 0 liquidity => false
    Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

    // 1 liquidity => false
    mix.registerInput(
        new ConfirmedInput(
            new RegisteredInput("liquidity1", true, generateOutPoint(), "127.0.0.1"), null));
    Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

    // 2 liquidity => false : minMustMix not reached
    mix.registerInput(
        new ConfirmedInput(
            new RegisteredInput("liquidity2", true, generateOutPoint(), "127.0.0.1"), null));
    Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

    // 1 mustMix => true : minMustMix reached
    mix.registerInput(
        new ConfirmedInput(
            new RegisteredInput("mustMix1", false, generateOutPoint(), "127.0.0.1"), null));
    Assert.assertTrue(spyMixService.isRegisterInputReady(mix));
  }

  private TxOutPoint generateOutPoint() {
    TxOutPoint txOutPoint =
        new TxOutPoint(
            Utils.getRandomString(65),
            0,
            99999,
            99,
            null,
            testUtils.generateSegwitAddress().getBech32AsString());
    return txOutPoint;
  }
}
