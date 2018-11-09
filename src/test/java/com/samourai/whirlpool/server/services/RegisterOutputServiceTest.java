package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
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
public class RegisterOutputServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired private RegisterInputService registerInputService;

  @Autowired private ConfirmInputService confirmInputService;

  @Autowired private RegisterOutputService registerOutputService;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  private void registerInput(Mix mix, String username) throws Exception {
    String poolId = mix.getPool().getPoolId();

    ECKey ecKey =
        ECKey.fromPrivate(
            new BigInteger(
                "34069012401142361066035129995856280497224474312925604298733347744482107649210"));
    byte[] pubkey = ecKey.getPubKey();
    String signature = ecKey.signMessage(poolId);

    long inputBalance = mix.getPool().computeInputBalanceMin(false);
    TxOutPoint txOutPoint =
        rpcClientService.createAndMockTxOutPoint(
            new SegwitAddress(pubkey, cryptoService.getNetworkParameters()), inputBalance, 999);

    registerInputService.registerInput(
        poolId,
        username,
        pubkey,
        signature,
        txOutPoint.getHash(),
        txOutPoint.getIndex(),
        false,
        true);
  }

  private byte[] confirmInput(
      Mix mix, String username, String receiveAddress, RSABlindingParameters blindingParams)
      throws Exception {
    String mixId = mix.getMixId();

    // REGISTER_INPUT
    registerInput(mix, username);
    testUtils.assertMix(0, 1, mix); // mustMix confirming

    // blind bordereau
    byte[] blindedBordereau = clientCryptoService.blind(receiveAddress, blindingParams);

    // CONFIRM_INPUT
    confirmInputService.confirmInputOrQueuePool(mixId, username, blindedBordereau);
    testUtils.assertMix(1, 0, mix); // mustMix confirmed

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau =
        cryptoService.signBlindedOutput(blindedBordereau, mix.getKeyPair());
    return signedBlindedBordereau;
  }

  @Test
  public void registerOutput_shouldSuccessWhenValid() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";
    String receiveAddress = testUtils.createSegwitAddress().getBech32AsString();

    // blind bordereau
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau = confirmInput(mix, username, receiveAddress, blindingParams);

    // go REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assert.assertEquals(0, mix.getReceiveAddresses().size());

    // REGISTER_OUTPUT
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);
    registerOutputService.registerOutput(
        mix.computeInputsHash(), unblindedSignedBordereau, receiveAddress);

    // VERIFY
    Assert.assertEquals(1, mix.getReceiveAddresses().size()); // output registered
  }

  @Test
  public void registerOutput_shouldFailWhenBlindedBordereauFromInvalidPubkey() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";
    String receiveAddress = testUtils.createSegwitAddress().getBech32AsString();

    // blind bordereau
    RSAKeyParameters fakePublicKey = (RSAKeyParameters) cryptoService.generateKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(fakePublicKey); // blind from INVALID pubkey

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau = confirmInput(mix, username, receiveAddress, blindingParams);

    // go REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assert.assertEquals(0, mix.getReceiveAddresses().size());

    // TEST
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);
    try {
      registerOutputService.registerOutput(
          mix.computeInputsHash(), unblindedSignedBordereau, receiveAddress);
      Assert.assertTrue(false); // should throw exception
    } catch (Exception e) {
      // exception expected
      Assert.assertEquals("Invalid unblindedSignedBordereau", e.getMessage());
    }

    // VERIFY
    Assert.assertEquals(0, mix.getReceiveAddresses().size()); // output NOT registered
  }

  @Test
  public void registerOutput_shouldFailWhenBordereauFromPreviousMix() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";
    String receiveAddress = testUtils.createSegwitAddress().getBech32AsString();

    // blind bordereau
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    // get signed blinded bordereau from a first mix
    byte[] signedBlindedBordereauFirstMix =
        confirmInput(mix, username, receiveAddress, blindingParams);

    // *** NEW MIX ***
    mixService.__reset();
    mix = __getCurrentMix();

    // register input again
    serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
    byte[] signedBlindedBordereauSecondMix =
        confirmInput(mix, username, receiveAddress, blindingParams);

    // go REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assert.assertEquals(0, mix.getReceiveAddresses().size());

    // TEST: unblindedSignedBordereau from FIRST mix should be REJECTED
    byte[] unblindedSignedBordereauFirstMix =
        clientCryptoService.unblind(signedBlindedBordereauFirstMix, blindingParams);
    try {
      registerOutputService.registerOutput(
          mix.computeInputsHash(), unblindedSignedBordereauFirstMix, receiveAddress);
      Assert.assertTrue(false); // should throw exception
    } catch (Exception e) {
      // exception expected
      Assert.assertEquals("Invalid unblindedSignedBordereau", e.getMessage());
    }

    // VERIFY
    Assert.assertEquals(0, mix.getReceiveAddresses().size()); // output NOT registered

    // TEST: unblindedSignedBordereau from SECOND mix should be ACCEPTED
    byte[] unblindedSignedBordereauSecondMix =
        clientCryptoService.unblind(signedBlindedBordereauSecondMix, blindingParams);
    registerOutputService.registerOutput(
        mix.computeInputsHash(), unblindedSignedBordereauSecondMix, receiveAddress);

    // VERIFY
    Assert.assertEquals(1, mix.getReceiveAddresses().size()); // output registered
  }

  @Test
  public void registerOutput_shouldSuccessWhenInvalid() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";
    String receiveAddress = testUtils.createSegwitAddress().getBech32AsString();

    // blind bordereau
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau = confirmInput(mix, username, receiveAddress, blindingParams);

    // go REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assert.assertEquals(0, mix.getReceiveAddresses().size());

    // REGISTER_OUTPUT
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);

    // - fail on invalid inputsHash
    try {
      registerOutputService.registerOutput(
          "invalidInputsHash", unblindedSignedBordereau, receiveAddress); // INVALID inputsHash
      Assert.assertTrue(false);
    } catch (Exception e) {
      Assert.assertEquals("Mix not found for inputsHash", e.getMessage());
    }
    Assert.assertEquals(0, mix.getReceiveAddresses().size()); // output NOT registered

    // - fail on invalid bordereau
    try {
      byte[] fakeSignedBordereau = "invalidBordereau".getBytes(); // INVALID bordereau
      registerOutputService.registerOutput(
          mix.computeInputsHash(), fakeSignedBordereau, receiveAddress);
      Assert.assertTrue(false);
    } catch (Exception e) {
      Assert.assertEquals("Invalid unblindedSignedBordereau", e.getMessage());
    }

    // - fail on receiveAddress not related to bordereau
    try {
      registerOutputService.registerOutput(
          mix.computeInputsHash(),
          unblindedSignedBordereau,
          "tb1qnfhmn4vfgprfnsnz2fadfr48cquydjkz4wpfgq"); // INVALID receiveAddress
      Assert.assertTrue(false);
    } catch (Exception e) {
      Assert.assertEquals("Invalid unblindedSignedBordereau", e.getMessage());
    }

    // - success when valid
    registerOutputService.registerOutput(
        mix.computeInputsHash(), unblindedSignedBordereau, receiveAddress);
    Assert.assertEquals(1, mix.getReceiveAddresses().size()); // output registered
  }
}
