package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.InputPool;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import java.util.function.Function;
import org.bitcoinj.core.ECKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class RegisterInputServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired private RegisterInputService registerInputService;

  private static final int MIN_CONFIRMATIONS_MUSTMIX = 11;
  private static final int MIN_CONFIRMATIONS_LIQUIDITY = 22;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.getRegisterInput().setMinConfirmationsMustMix(MIN_CONFIRMATIONS_MUSTMIX);
    serverConfig.getRegisterInput().setMinConfirmationsLiquidity(MIN_CONFIRMATIONS_LIQUIDITY);
  }

  final Function<Boolean, TxOutPoint> runTestValidInput =
      (Boolean liquidity) -> {
        TxOutPoint txOutPoint = null;
        try {
          Mix mix = __getCurrentMix();
          String poolId = mix.getPool().getPoolId();
          String username = "user1";

          ECKey ecKey =
              ECKey.fromPrivate(
                  new BigInteger(
                      "34069012401142361066035129995856280497224474312925604298733347744482107649210"));
          String signature = ecKey.signMessage(poolId);

          long inputBalance = mix.getPool().computeInputBalanceMin(liquidity);
          int confirmations = liquidity ? MIN_CONFIRMATIONS_LIQUIDITY : MIN_CONFIRMATIONS_MUSTMIX;
          txOutPoint =
              rpcClientService.createAndMockTxOutPoint(
                  new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters()),
                  inputBalance,
                  confirmations);

          // TEST
          registerInputService.registerInput(
              poolId,
              username,
              signature,
              txOutPoint.getHash(),
              txOutPoint.getIndex(),
              liquidity,
              true);

        } catch (Exception e) {
          e.printStackTrace();
          Assert.assertTrue(false);
        }
        return txOutPoint;
      };

  @Test
  public void registerInput_shouldRegisterMustMixWhenValid() throws Exception {
    // TEST
    TxOutPoint txOutPoint = runTestValidInput.apply(false);

    // VERIFY
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();

    // mustMix should be registered
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 1, mix); // mustMix confirming
    Assert.assertTrue(mix.hasConfirmingInput(txOutPoint));
  }

  @Test
  public void registerInput_shouldQueueLiquidityWhenValid() throws Exception {
    // TEST
    TxOutPoint txOutPoint = runTestValidInput.apply(true);

    // VERIFY
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();
    InputPool liquidityPool = mix.getPool().getLiquidityQueue();

    // liquidity should be queued
    Assert.assertTrue(liquidityPool.hasInput(txOutPoint));
    testUtils.assertPool(0, 1, 0, pool); // mustMix queued
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldQueueMustMixWhenValidAndMixStarted() throws Exception {
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT); // mix already started

    // TEST
    TxOutPoint txOutPoint = runTestValidInput.apply(false);

    // VERIFY

    // mustMix should be registered
    testUtils.assertPool(1, 0, 0, pool); // mustMix queued
    testUtils.assertMixEmpty(mix);
    Assert.assertTrue(mix.getPool().getMustMixQueue().hasInput(txOutPoint));
  }

  @Test
  public void registerInput_shouldQueueLiquidityWhenValidAndMixStarted() throws Exception {
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT); // mix already started

    // TEST
    TxOutPoint txOutPoint = runTestValidInput.apply(true);

    // VERIFY
    InputPool liquidityPool = mix.getPool().getLiquidityQueue();

    // liquidity should be queued
    Assert.assertTrue(liquidityPool.hasInput(txOutPoint));
    testUtils.assertPool(0, 1, 0, pool); // mustMix queued
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldFailWhenInvalidPoolId() throws Exception {
    Mix mix = __getCurrentMix();

    String poolId = "INVALID"; // INVALID
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computeInputBalanceMin(false);
    TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);

    // TEST
    thrown.expect(IllegalInputException.class);
    thrown.expectMessage("Pool not found");
    registerInputService.registerInput(
        poolId, username, signature, txOutPoint.getHash(), txOutPoint.getIndex(), false, true);

    // VERIFY
    testUtils.assertPoolEmpty(mix.getPool());
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldQueueInputWhenMixStatusAlreadyStarted() throws Exception {
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());

    // all mixStatus != CONFIRM_INPUT
    for (MixStatus mixStatus : MixStatus.values()) {
      if (!MixStatus.CONFIRM_INPUT.equals(mixStatus)
          && !MixStatus.SUCCESS.equals(mixStatus)
          && !MixStatus.FAIL.equals(mixStatus)) {
        setUp();

        log.info("----- " + mixStatus + " -----");

        Mix mix = __getCurrentMix();
        String mixId = mix.getMixId();
        String poolId = mix.getPool().getPoolId();

        // set status
        mixService.changeMixStatus(mixId, mixStatus);

        // TEST
        String signature = ecKey.signMessage(poolId);
        long inputBalance = mix.getPool().computeInputBalanceMin(false);
        TxOutPoint txOutPoint =
            rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);
        registerInputService.registerInput(
            poolId, username, signature, txOutPoint.getHash(), txOutPoint.getIndex(), false, true);

        // VERIFY
        testUtils.assertPool(1, 0, 0, mix.getPool()); // mustMix queued
        testUtils.assertMixEmpty(mix);
      }
    }
  }

  @Test
  public void registerInput_shouldFailWhenInvalidSignature() throws Exception {
    Mix mix = __getCurrentMix();
    String poolId = mix.getPool().getPoolId();
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = "INVALID";

    long inputBalance = mix.getPool().computeInputBalanceMin(false);
    TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);

    // TEST
    thrown.expect(IllegalInputException.class);
    thrown.expectMessage("Invalid signature");
    registerInputService.registerInput(
        poolId, username, signature, txOutPoint.getHash(), txOutPoint.getIndex(), false, true);

    // VERIFY
    testUtils.assertPoolEmpty(mix.getPool());
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldFailWhenInvalidPubkey() throws Exception {
    Mix mix = __getCurrentMix();
    String poolId = mix.getPool().getPoolId();
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        testUtils.generateSegwitAddress(); // INVALID: not related to pubkey
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computeInputBalanceMin(false);
    TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);

    // TEST
    thrown.expect(IllegalInputException.class);
    thrown.expectMessage("Invalid signature");
    registerInputService.registerInput(
        poolId, username, signature, txOutPoint.getHash(), txOutPoint.getIndex(), false, true);

    // VERIFY
    testUtils.assertPoolEmpty(mix.getPool());
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldFailWhenDuplicateInputsSameMix() throws Exception {
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();
    String poolId = mix.getPool().getPoolId();
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computeInputBalanceMin(false);
    TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);

    // TEST
    registerInputService.registerInput(
        poolId, username, signature, txOutPoint.getHash(), txOutPoint.getIndex(), false, true);
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 1, mix); // confirming

    registerInputService.registerInput(
        poolId, username, signature, txOutPoint.getHash(), txOutPoint.getIndex(), false, true);
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 1, mix); // not confirming twice
  }

  @Test
  public void registerInput_shouldFailWhenBalanceTooLow() throws Exception {
    Mix mix = __getCurrentMix();
    String poolId = mix.getPool().getPoolId();
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computeInputBalanceMin(false) - 1; // BALANCE TOO LOW
    TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);

    // TEST
    thrown.expect(IllegalInputException.class);
    thrown.expectMessage("Invalid input balance");
    registerInputService.registerInput(
        poolId, username, signature, txOutPoint.getHash(), txOutPoint.getIndex(), false, true);

    // VERIFY
    testUtils.assertPoolEmpty(mix.getPool());
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldFailWhenBalanceTooHigh() throws Exception {
    Mix mix = __getCurrentMix();
    String poolId = mix.getPool().getPoolId();
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computeInputBalanceMax(false) + 1; // BALANCE TOO HIGH
    TxOutPoint txOutPoint = rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance);

    // TEST
    thrown.expect(IllegalInputException.class);
    thrown.expectMessage("Invalid input balance");
    registerInputService.registerInput(
        poolId, username, signature, txOutPoint.getHash(), txOutPoint.getIndex(), false, true);

    // VERIFY
    testUtils.assertPoolEmpty(mix.getPool());
    testUtils.assertMixEmpty(mix);
  }

  private void doRegisterInput(int confirmations, boolean liquidity) throws Exception {
    Mix mix = __getCurrentMix();
    String poolId = mix.getPool().getPoolId();
    String username = "user1";

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computeInputBalanceMin(false);
    // mock input with 0 confirmations
    TxOutPoint txOutPoint =
        rpcClientService.createAndMockTxOutPoint(inputAddress, inputBalance, confirmations);

    registerInputService.registerInput(
        poolId, username, signature, txOutPoint.getHash(), txOutPoint.getIndex(), liquidity, true);
  }

  @Test
  public void registerInput_shouldQueueUnconfirmedWhenZeroConfirmations() throws Exception {
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();

    // mustMix
    //        testUtils.assertPool(0, 0, 0, pool);
    doRegisterInput(0, false);
    testUtils.assertPool(0, 0, 1, pool);
    testUtils.assertMixEmpty(mix);

    // liquidity
    mix.getPool().getUnconfirmedQueue().peekRandom(); // reset
    testUtils.assertPool(0, 0, 0, pool);
    doRegisterInput(0, true);
    testUtils.assertPool(0, 0, 1, pool);
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldQueueUnconfirmedWhenLessConfirmations() throws Exception {
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMixEmpty(mix);

    // mustMix
    doRegisterInput(MIN_CONFIRMATIONS_MUSTMIX - 1, false);
    testUtils.assertPool(0, 0, 1, pool);
    testUtils.assertMixEmpty(mix);

    // liquidity
    mix.getPool().getUnconfirmedQueue().peekRandom(); // reset
    testUtils.assertPool(0, 0, 0, pool);
    doRegisterInput(MIN_CONFIRMATIONS_LIQUIDITY - 1, true);
    testUtils.assertPool(0, 0, 1, pool);
    testUtils.assertMixEmpty(mix);
  }

  @Test
  public void registerInput_shouldSuccessWhenMoreConfirmations() throws Exception {
    Mix mix = __getCurrentMix();
    Pool pool = mix.getPool();
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMixEmpty(mix);

    // mustMix
    doRegisterInput(MIN_CONFIRMATIONS_MUSTMIX + 1, false);
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 1, mix);

    // liquidity
    doRegisterInput(MIN_CONFIRMATIONS_LIQUIDITY + 1, true);
    testUtils.assertPool(0, 1, 0, pool);
    testUtils.assertMix(0, 1, mix);
  }

  // TODO test noSamouraiFeesCheck for liquidities vs feesCheck for mustMix
}
