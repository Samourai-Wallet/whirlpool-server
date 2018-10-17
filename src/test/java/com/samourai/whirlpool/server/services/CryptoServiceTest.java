package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.utils.Utils;
import org.bitcoinj.core.ECKey;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import java.security.PublicKey;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class CryptoServiceTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String PK_PEM = "-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC6rSk+FulbGcKT\n" +
            "ePQ5H0w+V0GgDn2CiFDj688816hBdlPxQFxhHP0qLRdRIkgX5nnix98BSNbEdRb3\n" +
            "5mgky4S6kjNs4o0oIrKjb0+y1Gn/MH5RG1eEVCojRQzIQDXxhBUc/kidE0a36wZQ\n" +
            "zsttw+pbcLt3MxXvbqHL70Wwz45mzkszgBG9Zg9BFTXF7qDPQUCImdcUkYMraDHD\n" +
            "zO5PotN3oFYJ1xx7+T0IAR3yw0TqSpz4vK4Io8/2iO3tqEHgWfQD4hKcMSR77oMI\n" +
            "BSR+Y2ZsvJr/FAUkC07/DbG739nkcpelXsEJQHVBJhZhgtOFVyxpYyDAi6dvazxg\n" +
            "X4EptTWLAgMBAAECggEAHvqWjLcTpxUkIldhEXZdWj5zuw08jI7KUbtJKxvPeybu\n" +
            "W5ZmiOKH1tnLHqFAR+BtG2fiMl3f75P41E84ZYbKsjA0ot4JNn8PmH9j7ABDzjVs\n" +
            "zlE/GaGNU/NlDCRab106fb3AgiSinenoTz0KKwnnWKEMKIYMnq0u4jTaeAhHEE3k\n" +
            "WF+ytRICwwN+pz85AFN/R/nh/D02zDeS1S6Jwib04j5HfT8VH8ri9IyFXFZpQzwz\n" +
            "H9l+Q7b3fNq0QPXK5nkj9gDR2tM/UpoxyMT1pkD4hmBL7DZ3/C8lKn6XOhgZc2dW\n" +
            "SfsZF3w4lBqA5cJYny1NlRbpm7tzw5uuNxOPJWlUUQKBgQD4QGVua70HX9mQhfGd\n" +
            "/Oc/YJmc1aUro9iRq6Aj3JYc8CXEY1Cd25jci1iwWIDLHxsgCvncCZjBULFp5dVN\n" +
            "WiSYSdeaEtBGz5hBMT6CsT4KBnFN5zboIfHOROjW5gBFXfjbpLHj2vrKyLm74+We\n" +
            "G97sRKWJhhQqFCp9UMgMYlaemwKBgQDAgMLefgBkyA95w85yOFX1EeT6kUuP08UM\n" +
            "b5B9PilexPMQhKDbVuSnMag+8eUJoTCeAscvGf5AYIBAmNHVIScwkc6fsn5iL7FW\n" +
            "fqp0W/xtzE9ZvrHjX+u0WvnaTi6uFIHo0fZEUPrjAYhcOfF3nP9CyEgiHQvVNVJx\n" +
            "i/dUrlA70QKBgQDtLuF6MVeGLy635TFm59Ws+MdrT7giTMXCz74N5VhKx6rdyqGg\n" +
            "YMnYlQ4kVjqfVtXctH/qmgSnVkhbTCqSX/isw4hJfYYe0YK/bqQxy9PhUix46NrN\n" +
            "yHi1waLQhyllHRaCDAWmFHcevc6u1Ftyx2AiTqf2D/M+DMxXtJGdO2tU1wKBgHfW\n" +
            "MHmNevVCTdABgx070Nb1QtRxataogHyTXyF4dwyWErJvviuNVl523UQCFhD+lWNo\n" +
            "W1MJHWw6Jt0PxWCmeN0Vh8mGtoKtKfqsc7RoJya7D5LQ0bC4X+Uw1WV/UjPwdEbZ\n" +
            "njM9LlHu/FJdh+Jsi8OpJq6F4n3h6ebhuSCwOyZhAoGAB2ae3g8rZ7svx61AHADY\n" +
            "9dDXTklDUxzuGfUDOsLQxMSDkTXmwv1y7raewLap7sSUWbZt/RQNiG5tYyfU57Jz\n" +
            "AqmHvsG0HzQXTPqj3+NzouitEtIV3zn90pybiTnfmL6S1aNZqiFCNAVFNswZTwCd\n" +
            "FSqZ7pgeAply9rggFR+TQ/g=\n" +
            "-----END PRIVATE KEY-----\n";

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
        Assert.assertEquals("MIICITANBgkqhkiG9w0BAQEFAAOCAg4AMIICCQKCAQEAuq0pPhbpWxnCk3j0OR9MPldBoA59gohQ4+vPPNeoQXZT8UBcYRz9Ki0XUSJIF+Z54sffAUjWxHUW9+ZoJMuEupIzbOKNKCKyo29PstRp/zB+URtXhFQqI0UMyEA18YQVHP5InRNGt+sGUM7LbcPqW3C7dzMV726hy+9FsM+OZs5LM4ARvWYPQRU1xe6gz0FAiJnXFJGDK2gxw8zuT6LTd6BWCdcce/k9CAEd8sNE6kqc+LyuCKPP9ojt7ahB4Fn0A+ISnDEke+6DCAUkfmNmbLya/xQFJAtO/w2xu9/Z5HKXpV7BCUB1QSYWYYLThVcsaWMgwIunb2s8YF+BKbU1iwKCAQAe+paMtxOnFSQiV2ERdl1aPnO7DTyMjspRu0krG897Ju5blmaI4ofW2cseoUBH4G0bZ+IyXd/vk/jUTzhlhsqyMDSi3gk2fw+Yf2PsAEPONWzOUT8ZoY1T82UMJFpvXTp9vcCCJKKd6ehPPQorCedYoQwohgyerS7iNNp4CEcQTeRYX7K1EgLDA36nPzkAU39H+eH8PTbMN5LVLonCJvTiPkd9PxUfyuL0jIVcVmlDPDMf2X5Dtvd82rRA9crmeSP2ANHa0z9SmjHIxPWmQPiGYEvsNnf8LyUqfpc6GBlzZ1ZJ+xkXfDiUGoDlwlifLU2VFumbu3PDm643E48laVRR", Utils.encodeBase64(pubKey.getEncoded()));
    }

    @Test
    public void verifyMessageSignature() throws Exception {
        ECKey ecKey = ECKey.fromPrivate(new BigInteger("34069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey = ecKey.getPubKey();
        String message = "hello foo";
        String signature = ecKey.signMessage(message);

        // TEST
        // valid
        Assert.assertTrue(cryptoService.verifyMessageSignature(pubkey, message, signature));

        // wrong signature
        Assert.assertFalse(cryptoService.verifyMessageSignature(pubkey, message, signature+"foo"));

        // wrong message
        Assert.assertFalse(cryptoService.verifyMessageSignature(pubkey, message+"foo", signature));

        // wrong pubkey
        ECKey ecKey2 = ECKey.fromPrivate(new BigInteger("24069012401142361066035129995856280497224474312925604298733347744482107649210"));
        byte[] pubkey2 = ecKey2.getPubKey();
        Assert.assertFalse(cryptoService.verifyMessageSignature(pubkey2, message, signature));
    }
}