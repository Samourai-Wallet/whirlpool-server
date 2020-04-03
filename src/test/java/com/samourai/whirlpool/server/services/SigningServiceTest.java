package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.client.mix.handler.PremixHandler;
import com.samourai.whirlpool.client.mix.handler.UtxoWithBalance;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class SigningServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired private RegisterInputService registerInputService;

  @Autowired private ConfirmInputService confirmInputService;

  @Autowired private RegisterOutputService registerOutputService;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    serverConfig.setTestMode(true); // TODO
  }

  @Test
  public void signing_success() throws Exception {
    // mix config
    Mix mix = __nextMix(1, 0, 1, __getCurrentMix().getPool()); // 1 user

    // prepare input
    ECKey ecKey = new ECKey();
    boolean liquidity = false;
    long inputBalance = mix.getPool().computePremixBalanceMin(liquidity);
    TxOutPoint txOutPoint =
        createAndMockTxOutPoint(new SegwitAddress(ecKey.getPubKey(), params), inputBalance, 10);

    // valid signature
    UtxoWithBalance utxoWithBalance =
        new UtxoWithBalance(txOutPoint.getHash(), txOutPoint.getIndex(), inputBalance);
    PremixHandler premixHandler = new PremixHandler(utxoWithBalance, ecKey, "userPreHash");

    // test
    String username = "user1";
    String[] witness64 = doSigning(mix, premixHandler, liquidity, txOutPoint, username);
    mixService.registerSignature(mix.getMixId(), username, witness64);

    // verify
    Assert.assertEquals(MixStatus.SUCCESS, mix.getMixStatus());
  }

  @Test
  public void signing_failOnUnknownUsername() throws Exception {
    // mix config
    Mix mix = __nextMix(1, 0, 1, __getCurrentMix().getPool()); // 1 user

    // prepare input
    ECKey ecKey = new ECKey();
    boolean liquidity = false;
    long inputBalance = mix.getPool().computePremixBalanceMin(liquidity);
    TxOutPoint txOutPoint =
        createAndMockTxOutPoint(new SegwitAddress(ecKey.getPubKey(), params), inputBalance, 10);

    // valid signature
    UtxoWithBalance utxoWithBalance =
        new UtxoWithBalance(txOutPoint.getHash(), txOutPoint.getIndex(), inputBalance);
    PremixHandler premixHandler = new PremixHandler(utxoWithBalance, ecKey, "userPreHash");

    // test
    try {
      String username = "user1";
      String[] witness64 = doSigning(mix, premixHandler, liquidity, txOutPoint, username);
      mixService.registerSignature(mix.getMixId(), "dummy", witness64); // invalid user
    } catch (IllegalInputException e) {
      // verify
      Assert.assertEquals("Input not found for signing username=dummy", e.getMessage());
      Assert.assertEquals(MixStatus.SIGNING, mix.getMixStatus());
      return;
    }
    Assert.assertTrue(false); // IllegalInputException expected
  }

  @Test
  public void signing_failOnDuplicate() throws Exception {
    // mix config
    Mix mix = __nextMix(2, 0, 2, __getCurrentMix().getPool()); // 2 users
    boolean liquidity = false;
    long inputBalance = mix.getPool().computePremixBalanceMin(liquidity);

    // trick to simulate one first user registered
    String firstUsername = "firstUser";
    TxOutPoint firstTxOutPoint =
        createAndMockTxOutPoint(testUtils.generateSegwitAddress(), inputBalance, 10);
    mix.registerInput(
        new ConfirmedInput(
            new RegisteredInput(firstUsername, false, firstTxOutPoint, "127.0.0.1", null),
            "userHash1"));
    mix.registerOutput(testUtils.generateSegwitAddress().getBech32AsString());

    // prepare input
    ECKey ecKey = new ECKey();
    TxOutPoint txOutPoint =
        createAndMockTxOutPoint(new SegwitAddress(ecKey.getPubKey(), params), inputBalance, 10);

    // valid signature
    UtxoWithBalance utxoWithBalance =
        new UtxoWithBalance(txOutPoint.getHash(), txOutPoint.getIndex(), inputBalance);
    PremixHandler premixHandler = new PremixHandler(utxoWithBalance, ecKey, "userPreHash");

    // test
    String username = "user1";
    String[] witness64 = doSigning(mix, premixHandler, liquidity, txOutPoint, username);
    mixService.registerSignature(mix.getMixId(), username, witness64); // valid
    try {
      mixService.registerSignature(mix.getMixId(), username, witness64); // duplicate signing
    } catch (IllegalInputException e) {
      // verify
      Assert.assertEquals("User already signed, username=" + username, e.getMessage());
      Assert.assertEquals(MixStatus.SIGNING, mix.getMixStatus());
      return;
    }
    Assert.assertTrue(false); // IllegalInputException expected
  }

  @Test
  public void signing_failOnInvalidSignedAmount() throws Exception {
    // mix config
    Mix mix = __nextMix(1, 0, 1, __getCurrentMix().getPool()); // 1 user

    // prepare input
    ECKey ecKey = new ECKey();
    boolean liquidity = false;
    long inputBalance = mix.getPool().computePremixBalanceMin(liquidity);
    TxOutPoint txOutPoint =
        createAndMockTxOutPoint(new SegwitAddress(ecKey.getPubKey(), params), inputBalance, 10);

    // invalid signature (invalid signed amount)
    UtxoWithBalance utxoWithBalance =
        new UtxoWithBalance(txOutPoint.getHash(), txOutPoint.getIndex(), inputBalance);
    PremixHandler premixHandler =
        new PremixHandler(utxoWithBalance, ecKey, "userPreHash") {
          @Override
          public void signTransaction(Transaction tx, int inputIndex, NetworkParameters params) {
            long spendAmount = 1234; // invalid signed amount
            TxUtil.getInstance().signInputSegwit(tx, inputIndex, ecKey, spendAmount, params);
          }
        };

    // test
    try {
      String username = "user1";
      String[] witness64 = doSigning(mix, premixHandler, liquidity, txOutPoint, username);
      mixService.registerSignature(mix.getMixId(), username, witness64);
    } catch (IllegalInputException e) {
      // verify
      Assert.assertEquals("Invalid signature", e.getMessage());
      Assert.assertEquals(MixStatus.SIGNING, mix.getMixStatus());
      return;
    }
    Assert.assertTrue(false); // IllegalInputException expected
  }

  @Test
  public void signing_failOnInvalidSignature() throws Exception {
    // mix config
    Mix mix = __nextMix(1, 0, 1, __getCurrentMix().getPool()); // 1 user

    // prepare input
    ECKey ecKey = new ECKey();
    boolean liquidity = false;
    long inputBalance = mix.getPool().computePremixBalanceMin(liquidity);
    TxOutPoint txOutPoint =
        createAndMockTxOutPoint(new SegwitAddress(ecKey.getPubKey(), params), inputBalance, 10);

    // invalid signature (invalid key)
    UtxoWithBalance utxoWithBalance =
        new UtxoWithBalance(txOutPoint.getHash(), txOutPoint.getIndex(), inputBalance);
    PremixHandler premixHandler =
        new PremixHandler(utxoWithBalance, ecKey, "userPreHash") {
          @Override
          public void signTransaction(Transaction tx, int inputIndex, NetworkParameters params) {
            TxUtil.getInstance()
                .signInputSegwit(tx, inputIndex, new ECKey(), inputBalance, params); // invalid key
          }
        };

    // test
    try {
      String username = "user1";
      String[] witness64 = doSigning(mix, premixHandler, liquidity, txOutPoint, username);
      mixService.registerSignature(mix.getMixId(), username, witness64);
    } catch (IllegalInputException e) {
      // verify
      Assert.assertEquals("Invalid signature", e.getMessage());
      Assert.assertEquals(MixStatus.SIGNING, mix.getMixStatus());
      return;
    }
    Assert.assertTrue(false); // IllegalInputException expected
  }

  private String[] doSigning(
      Mix mix,
      PremixHandler premixHandler,
      boolean liquidity,
      TxOutPoint txOutPoint,
      String username)
      throws Exception {
    String mixId = mix.getMixId();
    String poolId = mix.getPool().getPoolId();

    // register input
    String signature = premixHandler.signMessage(poolId);
    registerInputService.registerInput(
        poolId,
        username,
        signature,
        txOutPoint.getHash(),
        txOutPoint.getIndex(),
        liquidity,
        "127.0.0.1");

    // confirm input
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();
    byte[] blindedBordereau = clientCryptoService.blind(receiveAddress, blindingParams);
    byte[] signedBlindedBordereau =
        confirmInputService
            .confirmInputOrQueuePool(mixId, username, blindedBordereau, "userHash" + username)
            .get();

    // register output
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);
    registerOutputService.registerOutput(
        mix.computeInputsHash(), unblindedSignedBordereau, receiveAddress);

    // signing
    Transaction txToSign = new Transaction(params, mix.getTx().bitcoinSerialize());
    int inputIndex =
        TxUtil.getInstance().findInputIndex(txToSign, txOutPoint.getHash(), txOutPoint.getIndex());
    premixHandler.signTransaction(txToSign, inputIndex, params);
    txToSign.verify();
    String[] witness64 = ClientUtils.witnessSerialize64(txToSign.getWitness(inputIndex));
    return witness64;
  }
}
