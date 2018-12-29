package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.client.mix.handler.PremixHandler;
import com.samourai.whirlpool.client.mix.handler.UtxoWithBalance;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.utils.Utils;
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
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class SigningServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired
  private RegisterInputService registerInputService;

  @Autowired
  private ConfirmInputService confirmInputService;

  @Autowired
  private RegisterOutputService registerOutputService;

  @Test
  public void signing_success() throws Exception {
    // mix config
    serverConfig.setTestMode(true); // TODO
    Mix mix = __nextMix(1, 1, __getCurrentMix().getPool()); // 1 user

    // prepare input
    ECKey ecKey = new ECKey();
    boolean liquidity = false;
    long inputBalance = mix.getPool().computeInputBalanceMin(liquidity);
    TxOutPoint txOutPoint =
        rpcClientService.createAndMockTxOutPoint(
            new SegwitAddress(ecKey.getPubKey(), params),
            inputBalance,
            10);

    // valid signature
    UtxoWithBalance utxoWithBalance = new UtxoWithBalance(txOutPoint.getHash(), txOutPoint.getIndex(), inputBalance);
    PremixHandler premixHandler = new PremixHandler(utxoWithBalance, ecKey);

    // test
    doTestSignature(mix, premixHandler, liquidity, txOutPoint);

    // verify
    Assert.assertEquals(MixStatus.SUCCESS, mix.getMixStatus());
  }

  @Test
  public void signing_failOnInvalidSignedAmount() throws Exception {
    // mix config
    serverConfig.setTestMode(true); // TODO
    Mix mix = __nextMix(1, 1, __getCurrentMix().getPool()); // 1 user

    // prepare input
    ECKey ecKey = new ECKey();
    boolean liquidity = false;
    long inputBalance = mix.getPool().computeInputBalanceMin(liquidity);
    TxOutPoint txOutPoint =
        rpcClientService.createAndMockTxOutPoint(
            new SegwitAddress(ecKey.getPubKey(), params),
            inputBalance,
            10);

    // invalid signature (invalid signed amount)
    UtxoWithBalance utxoWithBalance = new UtxoWithBalance(txOutPoint.getHash(), txOutPoint.getIndex(), inputBalance);
    PremixHandler premixHandler = new PremixHandler(utxoWithBalance, ecKey){
      @Override
      public void signTransaction(Transaction tx, int inputIndex, NetworkParameters params) {
        long spendAmount = 1234; // invalid signed amount
        TxUtil.getInstance().signInputSegwit(tx, inputIndex, ecKey, spendAmount, params);
      }
    };

    // test
    try {
      doTestSignature(mix, premixHandler, liquidity, txOutPoint);
    } catch(IllegalInputException e) {
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
    serverConfig.setTestMode(true); // TODO
    Mix mix = __nextMix(1, 1, __getCurrentMix().getPool()); // 1 user

    // prepare input
    ECKey ecKey = new ECKey();
    boolean liquidity = false;
    long inputBalance = mix.getPool().computeInputBalanceMin(liquidity);
    TxOutPoint txOutPoint =
        rpcClientService.createAndMockTxOutPoint(
            new SegwitAddress(ecKey.getPubKey(), params),
            inputBalance,
            10);

    // invalid signature (invalid key)
    UtxoWithBalance utxoWithBalance = new UtxoWithBalance(txOutPoint.getHash(), txOutPoint.getIndex(), inputBalance);
    PremixHandler premixHandler = new PremixHandler(utxoWithBalance, ecKey){
      @Override
      public void signTransaction(Transaction tx, int inputIndex, NetworkParameters params) {
        TxUtil.getInstance().signInputSegwit(tx, inputIndex, new ECKey(), inputBalance, params); // invalid key
      }
    };

    // test
    try {
      doTestSignature(mix, premixHandler, liquidity, txOutPoint);
    } catch(IllegalInputException e) {
      // verify
      Assert.assertEquals("Invalid signature", e.getMessage());
      Assert.assertEquals(MixStatus.SIGNING, mix.getMixStatus());
      return;
    }
    Assert.assertTrue(false); // IllegalInputException expected
  }

  private void doTestSignature(Mix mix, PremixHandler premixHandler, boolean liquidity, TxOutPoint txOutPoint) throws Exception {
    String mixId = mix.getMixId();
    String poolId = mix.getPool().getPoolId();
    String username = "user1";

    // register input
    String signature = premixHandler.signMessage(poolId);
    registerInputService.registerInput(
        poolId,
        username,
        signature,
        txOutPoint.getHash(),
        txOutPoint.getIndex(),
        liquidity,
        true);

    // confirm input
    RSAKeyParameters serverPublicKey = (RSAKeyParameters) mix.getKeyPair().getPublic();
    RSABlindingParameters blindingParams =
        clientCryptoService.computeBlindingParams(serverPublicKey);
    String receiveAddress = testUtils.generateSegwitAddress().getBech32AsString();
    byte[] blindedBordereau = clientCryptoService.blind(receiveAddress, blindingParams);
    byte[] signedBlindedBordereau = confirmInputService.confirmInputOrQueuePool(mixId, username, blindedBordereau).get();

    // register output
    byte[] unblindedSignedBordereau =
        clientCryptoService.unblind(signedBlindedBordereau, blindingParams);
    registerOutputService.registerOutput(
        mix.computeInputsHash(), unblindedSignedBordereau, receiveAddress);

    // signing
    int inputIndex = 0;
    premixHandler.signTransaction(mix.getTx(), 0, params);
    mix.getTx().verify();

    // transmit
    String[] witness64 = ClientUtils.witnessSerialize64(mix.getTx().getWitness(inputIndex));
    byte[][] witness = Utils.computeWitness(witness64);
    mixService.registerSignature(mixId, username, witness);
  }
}
