package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
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
public class ConfirmInputServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired private RegisterInputService registerInputService;

  @Autowired private ConfirmInputService confirmInputService;

  @Autowired private RegisterOutputService registerOutputService;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.setTestMode(true);
  }

  private void registerInput(Mix mix, String username) throws Exception {
    String poolId = mix.getPool().getPoolId();

    ECKey ecKey = new ECKey();
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computePremixBalanceMin(false);
    TxOutPoint txOutPoint =
        createAndMockTxOutPoint(
            new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters()),
            inputBalance,
            999);

    registerInputService.registerInput(
        poolId,
        username,
        signature,
        txOutPoint.getHash(),
        txOutPoint.getIndex(),
        false,
        true,
        "127.0.0.1");
  }

  @Test
  public void confirmInput_shouldSuccessWhenValid() throws Exception {
    Mix mix = __getCurrentMix();
    String mixId = mix.getMixId();
    String username = "testusername";
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    // REGISTER_INPUT
    registerInput(mix, username);
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

  private RSABlindingParameters computeBlindingParams(Mix mix) {
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);
    return blindingParams;
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
    testUtils.assertMix(1, 0, mix);
    // just 1 mustMix confirmed, the other one is queued with "Current mix is full for inputs with
    // same hash"
  }
}
