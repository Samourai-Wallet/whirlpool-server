package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.beans.FailReason;
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
@SpringBootTest(webEnvironment = RANDOM_PORT)
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
    int anonymitySet = 2;
    Mix mix =
        __nextMix(
            denomination,
            feeValue,
            minerFeeMin,
            minerFeeCap,
            minerFeeMax,
            mustMixMin,
            liquidityMin,
            anonymitySet);

    // 0 mustMix => false
    Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

    // 1 mustMix => false
    mix.registerInput(
        new ConfirmedInput(
            new RegisteredInput("mustMix1", false, generateOutPoint(), "127.0.0.1"),
            null,
            "userHash1"));
    Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

    // 2 mustMix => true
    mix.registerInput(
        new ConfirmedInput(
            new RegisteredInput("mustMix2", false, generateOutPoint(), "127.0.0.1"),
            null,
            "userHash2"));
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
    int anonymitySet = 2;
    Mix mix =
        __nextMix(
            denomination,
            feeValue,
            minerFeeMin,
            minerFeeCap,
            minerFeeMax,
            mustMixMin,
            liquidityMin,
            anonymitySet);

    // 0 liquidity => false
    Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

    // 1 liquidity => false
    mix.registerInput(
        new ConfirmedInput(
            new RegisteredInput("liquidity1", true, generateOutPoint(), "127.0.0.1"),
            null,
            "userHashL1"));
    Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

    // 2 liquidity => false : minMustMix not reached
    mix.registerInput(
        new ConfirmedInput(
            new RegisteredInput("liquidity2", true, generateOutPoint(), "127.0.0.1"),
            null,
            "userHashL2"));
    Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

    // 1 mustMix => true : minMustMix reached
    mix.registerInput(
        new ConfirmedInput(
            new RegisteredInput("mustMix1", false, generateOutPoint(), "127.0.0.1"),
            null,
            "userHashM1"));
    Assert.assertTrue(spyMixService.isRegisterInputReady(mix));
  }

  @Test
  public void isRegisterInputReady_spentWhileRegisterInput() throws Exception {
    MixService spyMixService = Mockito.spy(mixService);
    mixService = spyMixService;

    long denomination = 200000000;
    long feeValue = 10000000;
    long minerFeeMin = 100;
    long minerFeeCap = 9500;
    long minerFeeMax = 10000;
    int mustMixMin = 1;
    int liquidityMin = 0;
    int anonymitySet = 2;
    Mix mix =
        __nextMix(
            denomination,
            feeValue,
            minerFeeMin,
            minerFeeCap,
            minerFeeMax,
            mustMixMin,
            liquidityMin,
            anonymitySet);

    // 0 mustMix => false
    Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

    // 1 mustMix => false
    ConfirmedInput mustMix1 =
        new ConfirmedInput(
            new RegisteredInput("mustMix1", false, generateOutPoint(), "127.0.0.1"),
            null,
            "userHash1");
    mix.registerInput(mustMix1);
    Assert.assertFalse(spyMixService.isRegisterInputReady(mix));

    // 2 mustMix => true
    ConfirmedInput mustMix2 =
        new ConfirmedInput(
            new RegisteredInput("mustMix2", false, generateOutPoint(), "127.0.0.1"),
            null,
            "userHash2");
    mix.registerInput(mustMix2);
    Assert.assertTrue(spyMixService.isRegisterInputReady(mix));
    Assert.assertEquals(2, mix.getNbInputs());

    String blameIdentifierMustMix1 = Utils.computeBlameIdentitifer(mustMix1);
    Assert.assertTrue(dbService.findBlames(blameIdentifierMustMix1).isEmpty()); // no blame

    // mustMix spent in meantime => false
    TxOutPoint out1 = mustMix1.getRegisteredInput().getOutPoint();
    rpcClientService.mockSpentOutput(out1.getHash(), out1.getIndex());

    Assert.assertFalse(spyMixService.isRegisterInputReady(mix)); // mix not valid anymore
    Assert.assertEquals(1, mix.getNbInputs());

    // no blame as mix was not started yet
    Assert.assertEquals(dbService.findBlames(blameIdentifierMustMix1).size(), 0);

    // 2 mustMix => true
    ConfirmedInput mustMix3 =
        new ConfirmedInput(
            new RegisteredInput("mustMix3", false, generateOutPoint(), "127.0.0.1"),
            null,
            "userHash3");
    mix.registerInput(mustMix3);
    Assert.assertTrue(spyMixService.isRegisterInputReady(mix));
    Assert.assertEquals(2, mix.getNbInputs());

    // REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);

    // mustMix spent in meantime => false
    TxOutPoint out3 = mustMix3.getRegisteredInput().getOutPoint();
    rpcClientService.mockSpentOutput(out3.getHash(), out3.getIndex());

    Assert.assertFalse(spyMixService.isRegisterInputReady(mix)); // mix not valid + trigger fail

    // mix failed
    Assert.assertEquals(MixStatus.FAIL, mix.getMixStatus());
    Assert.assertEquals(FailReason.SPENT, mix.getFailReason());
    Assert.assertEquals(out3.getHash() + ":" + out3.getIndex(), mix.getFailInfo());

    // blame as mix was already started
    String blameIdentifierMustMix3 = Utils.computeBlameIdentitifer(mustMix3);
    Assert.assertEquals(dbService.findBlames(blameIdentifierMustMix3).size(), 1);
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
