package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import java.security.PublicKey;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
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
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class CryptoServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String PK_PEM =
      "-----BEGIN PRIVATE KEY-----\n"
          + "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC6rSk+FulbGcKT\n"
          + "ePQ5H0w+V0GgDn2CiFDj688816hBdlPxQFxhHP0qLRdRIkgX5nnix98BSNbEdRb3\n"
          + "5mgky4S6kjNs4o0oIrKjb0+y1Gn/MH5RG1eEVCojRQzIQDXxhBUc/kidE0a36wZQ\n"
          + "zsttw+pbcLt3MxXvbqHL70Wwz45mzkszgBG9Zg9BFTXF7qDPQUCImdcUkYMraDHD\n"
          + "zO5PotN3oFYJ1xx7+T0IAR3yw0TqSpz4vK4Io8/2iO3tqEHgWfQD4hKcMSR77oMI\n"
          + "BSR+Y2ZsvJr/FAUkC07/DbG739nkcpelXsEJQHVBJhZhgtOFVyxpYyDAi6dvazxg\n"
          + "X4EptTWLAgMBAAECggEAHvqWjLcTpxUkIldhEXZdWj5zuw08jI7KUbtJKxvPeybu\n"
          + "W5ZmiOKH1tnLHqFAR+BtG2fiMl3f75P41E84ZYbKsjA0ot4JNn8PmH9j7ABDzjVs\n"
          + "zlE/GaGNU/NlDCRab106fb3AgiSinenoTz0KKwnnWKEMKIYMnq0u4jTaeAhHEE3k\n"
          + "WF+ytRICwwN+pz85AFN/R/nh/D02zDeS1S6Jwib04j5HfT8VH8ri9IyFXFZpQzwz\n"
          + "H9l+Q7b3fNq0QPXK5nkj9gDR2tM/UpoxyMT1pkD4hmBL7DZ3/C8lKn6XOhgZc2dW\n"
          + "SfsZF3w4lBqA5cJYny1NlRbpm7tzw5uuNxOPJWlUUQKBgQD4QGVua70HX9mQhfGd\n"
          + "/Oc/YJmc1aUro9iRq6Aj3JYc8CXEY1Cd25jci1iwWIDLHxsgCvncCZjBULFp5dVN\n"
          + "WiSYSdeaEtBGz5hBMT6CsT4KBnFN5zboIfHOROjW5gBFXfjbpLHj2vrKyLm74+We\n"
          + "G97sRKWJhhQqFCp9UMgMYlaemwKBgQDAgMLefgBkyA95w85yOFX1EeT6kUuP08UM\n"
          + "b5B9PilexPMQhKDbVuSnMag+8eUJoTCeAscvGf5AYIBAmNHVIScwkc6fsn5iL7FW\n"
          + "fqp0W/xtzE9ZvrHjX+u0WvnaTi6uFIHo0fZEUPrjAYhcOfF3nP9CyEgiHQvVNVJx\n"
          + "i/dUrlA70QKBgQDtLuF6MVeGLy635TFm59Ws+MdrT7giTMXCz74N5VhKx6rdyqGg\n"
          + "YMnYlQ4kVjqfVtXctH/qmgSnVkhbTCqSX/isw4hJfYYe0YK/bqQxy9PhUix46NrN\n"
          + "yHi1waLQhyllHRaCDAWmFHcevc6u1Ftyx2AiTqf2D/M+DMxXtJGdO2tU1wKBgHfW\n"
          + "MHmNevVCTdABgx070Nb1QtRxataogHyTXyF4dwyWErJvviuNVl523UQCFhD+lWNo\n"
          + "W1MJHWw6Jt0PxWCmeN0Vh8mGtoKtKfqsc7RoJya7D5LQ0bC4X+Uw1WV/UjPwdEbZ\n"
          + "njM9LlHu/FJdh+Jsi8OpJq6F4n3h6ebhuSCwOyZhAoGAB2ae3g8rZ7svx61AHADY\n"
          + "9dDXTklDUxzuGfUDOsLQxMSDkTXmwv1y7raewLap7sSUWbZt/RQNiG5tYyfU57Jz\n"
          + "AqmHvsG0HzQXTPqj3+NzouitEtIV3zn90pybiTnfmL6S1aNZqiFCNAVFNswZTwCd\n"
          + "FSqZ7pgeAply9rggFR+TQ/g=\n"
          + "-----END PRIVATE KEY-----\n";

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void pkPEM() throws Exception {
    // TEST
    AsymmetricCipherKeyPair pk = testUtils.readPkPEM(PK_PEM);
    String pem = testUtils.computePkPEM(pk);

    // VERIFY
    Assert.assertEquals(PK_PEM, pem);
  }

  @Test
  public void computePublicKey() throws Exception {
    AsymmetricCipherKeyPair pk = testUtils.readPkPEM(PK_PEM);

    // TEST
    PublicKey pubKey = cryptoService.computePublicKey(pk);

    // VERIFY
    Assert.assertEquals(
        "fOfuHfBXi)dUZGG{ydc70r&D9F=VzZfOfuj0!HBN0j]oYj@ix!8n.OZ]F[0!k374y4T&}+p#*(/jLcI$C2N]@tWXSJdLhDlb1JKjDe=)40z7!WBR:-lxE7=OX@[<=&/bugVzhtlVEAQ*fN=>rsa<K5bsJ3ZkSe7+6=^izOFaG4(JQhh+wCeZtx4h#gzPv@Q08)[U*ntw=pY%z5XR=!k[#n@)X4((k.$&W6P[1#xFEAG)OxOaCC4$y/bPekjQq6e]20(Xn$tW?T{y/G{k5b6S6PuA]Aj7=Og#po)T>=GbZ%lny}Mkp6A]-&px7^dYwIr5A*JRe.3JwHk{^vQG42APennPpZ(baeyDM0iFHN#:IVOFh03q05JjfE#6^K#8vg^V*t0v2t4lPPf+2b=xd&V/YcHL97w@bD4/4Fj/P*hcaz5WcEgf@/aLLAT6iaW&!Vm[bf?u=8&514&R(&oz#heHMnkn*eOq$BMebWba(i^[YSF*v7Y(f7]a3m=CCsFlcKH76VQf5!.wCNc!Jp2]z-VB7qD.VLv=kreZxE[fs[@5(%Zh<hYzInyRi&YY>Xknto.&}eg-tVLMajtO8jENyNrEa>})]#=AxbLzb+*s5n:NLz[4]}>1HHg0WvhH26Lb}swgiV5-Qxi4R385EVWLP28l.DrAZo{DHUO4A:VO31W-K0OHtq2",
        WhirlpoolProtocol.encodeBytes(pubKey.getEncoded()));
  }

  @Test
  public void verifyUnblindedSignedBordereau_shouldSuccessWhenValid() throws Exception {
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();
    AsymmetricCipherKeyPair serverKeyPair = cryptoService.generateKeyPair();
    RSAKeyParameters serverPubKey = (RSAKeyParameters) serverKeyPair.getPublic();

    // blind
    RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPubKey);
    byte[] blindedBordereau = clientCryptoService.blind(receiveAddress, blindingParams);

    // sign
    byte[] signedBlindedBordereau =
        cryptoService.signBlindedOutput(blindedBordereau, serverKeyPair);

    // unblind
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);

    // verify
    Assert.assertTrue(
        cryptoService.verifyUnblindedSignedBordereau(
            receiveAddress, unblindedSignedBordereau, serverKeyPair));
  }

  @Test
  public void verifyUnblindedSignedBordereau_shouldFailWhenInvalidUnblindedData() throws Exception {
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();
    AsymmetricCipherKeyPair serverKeyPair = cryptoService.generateKeyPair();
    RSAKeyParameters serverPubKey = (RSAKeyParameters) serverKeyPair.getPublic();

    // blind
    RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPubKey);
    byte[] blindedBordereau = clientCryptoService.blind(receiveAddress, blindingParams);

    // sign
    byte[] signedBlindedBordereau =
        cryptoService.signBlindedOutput(blindedBordereau, serverKeyPair);

    // unblind
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);

    // verify
    String fakeReceiveAddress = "fakeReceiveAddress";
    Assert.assertFalse(
        cryptoService.verifyUnblindedSignedBordereau(
            fakeReceiveAddress, unblindedSignedBordereau, serverKeyPair)); // reject
  }

  @Test
  public void verifyUnblindedSignedBordereau_shouldFailWhenUnblindInvalidBlindingParams()
      throws Exception {
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();
    AsymmetricCipherKeyPair serverKeyPair = cryptoService.generateKeyPair();
    RSAKeyParameters serverPubKey = (RSAKeyParameters) serverKeyPair.getPublic();

    // blind
    RSABlindingParameters blindingParams = clientCryptoService.computeBlindingParams(serverPubKey);
    byte[] blindedBordereau = clientCryptoService.blind(receiveAddress, blindingParams);

    // sign
    byte[] signedBlindedBordereau =
        cryptoService.signBlindedOutput(blindedBordereau, serverKeyPair);

    // unblind
    RSABlindingParameters fakeBlindingParams =
        clientCryptoService.computeBlindingParams(serverPubKey);
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(
            signedBlindedBordereau, fakeBlindingParams); // unblind from different blindingParams

    // verify
    Assert.assertFalse(
        cryptoService.verifyUnblindedSignedBordereau(
            receiveAddress, unblindedSignedBordereau, serverKeyPair)); // reject
  }

  @Test
  public void verifyUnblindedSignedBordereau_shouldFailWhenBlindFromInvalidPubkey()
      throws Exception {
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();
    AsymmetricCipherKeyPair serverKeyPair = cryptoService.generateKeyPair();

    // blind
    RSAKeyParameters fakePubKey = (RSAKeyParameters) cryptoService.generateKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(fakePubKey); // blind from wrong pubKey
    byte[] blindedBordereau = clientCryptoService.blind(receiveAddress, blindingParams);

    // sign
    byte[] signedBlindedBordereau =
        cryptoService.signBlindedOutput(blindedBordereau, serverKeyPair);

    // unblind
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);

    // verify
    Assert.assertFalse(
        cryptoService.verifyUnblindedSignedBordereau(
            receiveAddress, unblindedSignedBordereau, serverKeyPair)); // reject
  }
}
