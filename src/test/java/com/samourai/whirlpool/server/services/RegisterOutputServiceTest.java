package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.integration.AbstractMixIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
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
public class RegisterOutputServiceTest extends AbstractMixIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Before
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.setTestMode(true);
  }

  @Test
  public void registerOutput_shouldSuccessWhenValid() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    // blind bordereau
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau =
        registerInputAndConfirmInput(mix, username, 999, false, receiveAddress, blindingParams);

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
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    // blind bordereau
    RSAKeyParameters fakePublicKey = (RSAKeyParameters) cryptoService.generateKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(fakePublicKey); // blind from INVALID pubkey

    // get a valid signed blinded bordereau
    byte[] signedBlindedBordereau =
        registerInputAndConfirmInput(mix, username, 999, false, receiveAddress, blindingParams);

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
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    // blind bordereau
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    // get signed blinded bordereau from a first mix
    byte[] signedBlindedBordereauFirstMix =
        registerInputAndConfirmInput(mix, username, 999, false, receiveAddress, blindingParams);

    // *** NEW MIX ***
    mixService.__reset();
    mix = __getCurrentMix();

    // register input again
    serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
    byte[] signedBlindedBordereauSecondMix =
        registerInputAndConfirmInput(mix, username, 999, false, receiveAddress, blindingParams);

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
  public void registerOutput_shouldFailWhenInvalid() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();

    // blind bordereau
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    // REGISTER_INPUT + CONFIRM_INPUT
    byte[] signedBlindedBordereau =
        registerInputAndConfirmInput(mix, username, 999, false, receiveAddress, blindingParams);

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
      Assert.assertEquals("REGISTER_OUTPUT too late, mix is over", e.getMessage());
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

  @Test
  public void registerOutput_shouldFailWhenReuseInputAddress() throws Exception {
    Mix mix = __getCurrentMix();
    String username = "testusername";

    // blind bordereau
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);

    // REGISTER_INPUT + CONFIRM_INPUT
    byte[] signedBlindedBordereau =
        registerInputAndConfirmInput(
            mix, username, 999, false, null, blindingParams); // reuse input address

    // go REGISTER_OUTPUT
    mix.setMixStatusAndTime(MixStatus.REGISTER_OUTPUT);
    Assert.assertEquals(0, mix.getReceiveAddresses().size());

    // REGISTER_OUTPUT
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);

    // - fail on receiveAddress reuse from inputs
    try {
      String reusedAddress =
          mix.getInputs().iterator().next().getRegisteredInput().getOutPoint().getToAddress();
      registerOutputService.registerOutput(
          mix.computeInputsHash(),
          unblindedSignedBordereau,
          reusedAddress); // INVALID receiveAddress
      Assert.assertTrue(false);
    } catch (Exception e) {
      Assert.assertEquals("receiveAddress already registered as input", e.getMessage());
    }
  }
}
