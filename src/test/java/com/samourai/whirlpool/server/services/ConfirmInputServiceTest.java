package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.integration.AbstractMixIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class ConfirmInputServiceTest extends AbstractMixIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Before
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.setTestMode(true);
  }

  @Test
  public void confirmInput_shouldSuccessWhenValid() throws Exception {
    Mix mix = __getCurrentMix();
    String mixId = mix.getMixId();
    String username = "testusername";
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    // REGISTER_INPUT
    registerInput(mix, username, 999, false);
    testUtils.assertMix(0, 1, mix); // mustMix confirming

    // blind bordereau
    RSABlindingParameters blindingParams = computeBlindingParams(mix);
    byte[] blindedBordereau = clientCryptoService.blind(receiveAddress, blindingParams);

    // CONFIRM_INPUT
    confirmInputService.confirmInputOrQueuePool(mixId, username, blindedBordereau);

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau =
        cryptoService.signBlindedOutput(blindedBordereau, mix.getKeyPair());

    // REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assert.assertEquals(0, mix.getReceiveAddresses().size());
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);
    registerOutputService.registerOutput(
        mix.computeInputsHash(), unblindedSignedBordereau, receiveAddress);
    Assert.assertEquals(1, mix.getReceiveAddresses().size());

    // TEST

    // VERIFY
    testUtils.assertMix(1, 0, mix); // mustMix confirmed
    Thread.sleep(5000);
  }

  @Test
  public void registerInput_shouldQueueWhenInputsSameHash() throws Exception {
    Mix mix = __getCurrentMix();
    String mixId = mix.getMixId();
    Pool pool = mix.getPool();
    String poolId = mix.getPool().getPoolId();
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    ECKey ecKey = new ECKey();
    SegwitAddress inputAddress =
        new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters());
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computePremixBalanceMin(false);

    // same hash
    RpcTransaction rpcTransaction =
        rpcClientService.createAndMockTx(inputAddress, inputBalance, 100, 2);
    TxOutPoint txOutPoint1 = blockchainDataService.getOutPoint(rpcTransaction, 0);
    TxOutPoint txOutPoint2 = blockchainDataService.getOutPoint(rpcTransaction, 1);

    Assert.assertEquals(txOutPoint1.getHash(), txOutPoint2.getHash());
    Assert.assertEquals(0, txOutPoint1.getIndex());
    Assert.assertEquals(1, txOutPoint2.getIndex());

    // TEST
    registerInputService.registerInput(
        poolId,
        "user1",
        signature,
        txOutPoint1.getHash(),
        txOutPoint1.getIndex(),
        false,
        true,
        "127.0.0.1");
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 1, mix); // confirming

    registerInputService.registerInput(
        poolId,
        "user2",
        signature,
        txOutPoint2.getHash(),
        txOutPoint2.getIndex(),
        false,
        true,
        "127.0.0.1");
    testUtils.assertPoolEmpty(pool);
    testUtils.assertMix(0, 2, mix); // confirming

    // blind bordereau
    RSABlindingParameters blindingParams = computeBlindingParams(mix);
    byte[] blindedBordereau = clientCryptoService.blind(receiveAddress, blindingParams);

    // CONFIRM_INPUT
    confirmInputService.confirmInputOrQueuePool(mixId, "user1", blindedBordereau);
    confirmInputService.confirmInputOrQueuePool(mixId, "user2", blindedBordereau);

    // VERIFY
    testUtils.assertMix(1, 0, mix); // 1 mustMix confirmed
    testUtils.assertPool(1, 0, pool); // 1 mustMix queued
  }

  @Test
  public void confirmInput_shouldQueueWhenMaxAnonymitySetReached() throws Exception {
    Pool pool = __getCurrentMix().getPool();
    Mix mix = __nextMix(1, 0, 2, pool); // 2 mustMix max

    // 1/2
    registerInputAndConfirmInput(mix, "user1", 999, false, null, null);
    testUtils.assertMix(1, 0, mix); // mustMix confirmed
    testUtils.assertPool(0, 0, pool);

    // 2/2
    registerInputAndConfirmInput(mix, "user2", 999, false, null, null);
    testUtils.assertMix(2, 0, mix); // mustMix confirmed
    testUtils.assertPool(0, 0, pool);

    // 3/2 => queued
    registerInputAndConfirmInput(mix, "user3", 999, false, null, null);
    testUtils.assertMix(2, 0, mix); // mustMix queued
  }

  @Test
  public void confirmInput_shouldQueueWhenMaxMustMixReached() throws Exception {
    Mix mix =
        __nextMix(
            1, 1, 2, __getCurrentMix().getPool()); // 2 users max - 1 liquidityMin = 1 mustMix max
    Pool pool = mix.getPool();

    // 1/2 mustMix
    registerInputAndConfirmInput(mix, "mustMix1", 999, false, null, null);
    testUtils.assertMix(1, 0, mix); // mustMix confirmed
    testUtils.assertPool(0, 0, pool);

    // 2/2 mustMix => queued
    registerInputAndConfirmInput(mix, "mustMix2", 999, false, null, null);
    testUtils.assertMix(1, 0, mix); // mustMix queued
    testUtils.assertPool(1, 0, pool);

    // 1/1 liquidity
    registerInputAndConfirmInput(mix, "liquidity1", 999, true, null, null);
    testUtils.assertMix(1, 0, mix); // liquidity queued
    testUtils.assertPool(1, 1, pool);

    // wait for liquidityWatcher to invite liquidity
    Thread.sleep(serverConfig.getRegisterInput().getLiquidityInterval() * 1000);
    testUtils.assertMix(1, 1, mix); // liquidity confirming
    testUtils.assertPool(1, 0, pool);
  }
}
