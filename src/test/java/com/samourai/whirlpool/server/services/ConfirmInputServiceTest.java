package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.Mix;
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

  private TxOutPoint registerInput(Mix mix, String username, ECKey ecKey, TxOutPoint txOutPoint, String resumeConfirmedMixId) throws Exception {
    String poolId = mix.getPool().getPoolId();

    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computePremixBalanceMin(false);
    if (txOutPoint == null) {
      txOutPoint =
          rpcClientService.createAndMockTxOutPoint(
              new SegwitAddress(ecKey.getPubKey(), cryptoService.getNetworkParameters()),
              inputBalance,
              999);
    }

    registerInputService.registerInput(
        poolId, username, signature, txOutPoint.getHash(), txOutPoint.getIndex(), false, true, resumeConfirmedMixId);

    return txOutPoint;
  }

  private byte[] confirmInput(Mix mix, String username, String receiveAddress, RSABlindingParameters blindingParams) throws Exception {
    // blind bordereau
    byte[] blindedBordereau = clientCryptoService.blind(receiveAddress, blindingParams);

    // CONFIRM_INPUT
    confirmInputService.confirmInputOrQueuePool(mix.getMixId(), username, blindedBordereau);
    return blindedBordereau;
  }

  @Test
  public void confirmInput_shouldSuccessWhenValid() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    // REGISTER_INPUT
    ECKey ecKey = new ECKey();
    registerInput(mix, username, ecKey, null, null);
    testUtils.assertMix(0, 1, mix); // mustMix confirming

    // CONFIRM_INPUT
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    byte[] blindedBordereau = confirmInput(mix, username, receiveAddress, blindingParams);

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
  }

  @Test
  public void resumeConfirmedInput_shouldSuccessWhenValid() throws Exception {
    Mix mix = __getCurrentMix();
    String mixId = mix.getMixId();
    String username = "testusername";
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    // REGISTER_INPUT
    ECKey ecKey = new ECKey();
    TxOutPoint txOutPoint = registerInput(mix, username, ecKey, null, null);
    testUtils.assertMix(0, 1, mix); // mustMix confirming

    // CONFIRM_INPUT
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    byte[] blindedBordereau = confirmInput(mix, username, receiveAddress, blindingParams);

    // input is confirmed
    testUtils.assertMix(1, 0, mix);

    // => REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);

    // simulate input disconnect
    mixService.onClientDisconnect(username);

    // verify input still here but offlined
    testUtils.assertMix(1, 0, mix);
    Assert.assertTrue(mix.getInputs().iterator().next().isOffline());

    // resume confirmed input (REGISTER_INPUT again)
    String resumeConfirmedMixId = mixId;
    registerInput(mix, username, ecKey, txOutPoint, resumeConfirmedMixId);

    // verify mustMix already confirmed & back online
    testUtils.assertMix(1, 0, mix);
    Assert.assertFalse(mix.getInputs().iterator().next().isOffline());

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau =
        cryptoService.signBlindedOutput(blindedBordereau, mix.getKeyPair());

    // REGISTER_OUTPUT
    Assert.assertEquals(0, mix.getReceiveAddresses().size());
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);
    registerOutputService.registerOutput(
        mix.computeInputsHash(), unblindedSignedBordereau, receiveAddress);
    Assert.assertEquals(1, mix.getReceiveAddresses().size());

    // TEST

    // VERIFY
    testUtils.assertMix(1, 0, mix); // mustMix confirmed
  }
}
