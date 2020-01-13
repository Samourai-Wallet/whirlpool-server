package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.wallet.client.Bip84Wallet;
import com.samourai.wallet.client.indexHandler.MemoryIndexHandler;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.tx0.*;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.beans.Tx0Data;
import com.samourai.whirlpool.protocol.fee.WhirlpoolFeeData;
import com.samourai.whirlpool.server.beans.PoolFee;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java8.util.Lists;
import org.bitcoinj.core.Transaction;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class FeeValidationServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final long FEES_VALID = 975000;

  private static final String SCODE_FOO_0 = "foo";
  private static final short SCODE_FOO_PAYLOAD = 1234;
  private static final String SCODE_BAR_25 = "bar";
  private static final short SCODE_BAR_PAYLOAD = 5678;
  private static final String SCODE_MIN_50 = "min";
  private static final short SCODE_MIN_PAYLOAD = -32768;
  private static final String SCODE_MAX_80 = "maX";
  private static final short SCODE_MAX_PAYLOAD = 32767;

  private WhirlpoolWalletConfig whirlpoolWalletConfig;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dbService.__reset();

    // scodes
    setScodeConfig(SCODE_FOO_0, SCODE_FOO_PAYLOAD, 0, null);
    setScodeConfig(SCODE_BAR_25, SCODE_BAR_PAYLOAD, 25, null);
    setScodeConfig(SCODE_MIN_50, SCODE_MIN_PAYLOAD, 50, null);
    setScodeConfig(SCODE_MAX_80, SCODE_MAX_PAYLOAD, 80, null);

    whirlpoolWalletConfig = new WhirlpoolWalletConfig(null, null, null, null, params, false, null);
  }

  private void assertFeeData(String txid, Integer feeIndice, byte[] feePayload) {
    RpcTransaction rpcTransaction =
        blockchainDataService
            .getRpcTransaction(txid)
            .orElseThrow(() -> new NoSuchElementException());
    WhirlpoolFeeData feeData = feeValidationService.decodeFeeData(rpcTransaction.getTx());
    if (feeIndice == null && feePayload == null) {
      Assert.assertNull(feeData);
    } else {
      Assert.assertEquals((int) feeIndice, feeData.getFeeIndice());
      if (feePayload == null) {
        Assert.assertNull(feeData.getFeePayload());
      } else {
        Assert.assertArrayEquals(feePayload, feeData.getFeePayload());
      }
    }
  }

  @Test
  public void findFeeData() {
    //
    assertFeeData("cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187", null, null);
    assertFeeData("7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16", null, null);
    assertFeeData("5369dfb71b36ed2b91ca43f388b869e617558165e4f8306b80857d88bdd624f2", null, null);
    assertFeeData(
        "b3557587f87bcbd37e847a0fff0ded013b23026f153d85f28cb5d407d39ef2f3",
        11,
        Utils.feePayloadShortToBytes((short) 12345));
    assertFeeData("aa77a502ca48540706c6f4a62f6c7155ee415c344a4481e0bf945fb56bbbdfdd", 12, null);
    assertFeeData("604dac3fa5f83b810fc8f4e8d94d9283e4d0b53e3831d0fe6dc9ecdb15dd8dfb", 13, null);
  }

  @Test
  public void computeFeeAddress() {
    Assert.assertEquals(
        feeValidationService.computeFeeAddress(1), "tb1qz84ma37y3d759sdy7mvq3u4vsxlg2qahw3lm23");
    Assert.assertEquals(
        feeValidationService.computeFeeAddress(2), "tb1qk20n7cpza4eakn0vqyfwskdgpwwwy29l5ek93w");
    Assert.assertEquals(
        feeValidationService.computeFeeAddress(3), "tb1qs394xtk0cx8ls9laymdn3scdrss3c2c20gttdx");
  }

  @Test
  public void isTx0FeePaid_feeValue() throws Exception {
    String txid = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";

    // accept when paid exact fee
    Assert.assertTrue(doIsTx0FeePaid(txid, 1234, FEES_VALID, 1, null, 100));

    // accept when paid more than fee
    Assert.assertTrue(doIsTx0FeePaid(txid, 1234, FEES_VALID - 1, 1, null, 100));
    Assert.assertTrue(doIsTx0FeePaid(txid, 1234, 1, 1, null, 100));

    // reject when paid less than fee
    Assert.assertFalse(doIsTx0FeePaid(txid, 1234, FEES_VALID + 1, 1, null, 100));
    Assert.assertFalse(doIsTx0FeePaid(txid, 1234, 1000000, 1, null, 100));

    // reject when paid to wrong xpub indice
    Assert.assertFalse(doIsTx0FeePaid(txid, 234, FEES_VALID, 0, null, 100));
    Assert.assertFalse(doIsTx0FeePaid(txid, 234, FEES_VALID, 2, null, 100));
    Assert.assertFalse(doIsTx0FeePaid(txid, 234, FEES_VALID, 10, null, 100));
  }

  @Test
  public void isTx0FeePaid_feeAccept() throws Exception {
    String txid = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";
    Map<Long, Long> feeAccept = new HashMap<>();
    feeAccept.put(FEES_VALID, 11111111L);

    // reject when no feeAccept
    Assert.assertFalse(doIsTx0FeePaid(txid, 1234, FEES_VALID + 10, 1, null, 100));

    // accept when tx0Time <= feeAccept.maxTime
    Assert.assertTrue(doIsTx0FeePaid(txid, 11111110L, FEES_VALID + 10, 1, feeAccept, 100));
    Assert.assertTrue(doIsTx0FeePaid(txid, 11110L, FEES_VALID + 10, 1, feeAccept, 100));

    // reject when tx0Time > feeAccept.maxTime
    Assert.assertFalse(doIsTx0FeePaid(txid, 11111112L, FEES_VALID + 10, 1, feeAccept, 100));
  }

  private boolean doIsTx0FeePaid(
      String txid,
      long txTime,
      long minFees,
      int xpubIndice,
      Map<Long, Long> feeAccept,
      int feeValuePercent) {
    PoolFee poolFee = new PoolFee(minFees, feeAccept);
    return feeValidationService.isTx0FeePaid(
        getTx(txid), txTime, xpubIndice, poolFee, feeValuePercent);
  }

  private Transaction getTx(String txid) {
    RpcTransaction rpcTransaction =
        blockchainDataService
            .getRpcTransaction(txid)
            .orElseThrow(() -> new NoSuchElementException());
    return rpcTransaction.getTx();
  }

  @Test
  public void isValidTx0_feePayloadValid() throws Exception {
    UnspentOutputWithKey spendFrom = testUtils.generateUnspentOutputWithKey(99000000, params);
    Collection<UnspentOutputWithKey> spendFroms = Lists.of(spendFrom);
    HD_Wallet bip84w =
        hdWalletFactory.restoreWallet(
            "all all all all all all all all all all all all", "test", 1, params);

    Bip84Wallet depositWallet =
        new Bip84Wallet(bip84w, 0, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet premixWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 2, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet postmixWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 4, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet badBankWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 3, new MemoryIndexHandler(), new MemoryIndexHandler());

    Pool pool = new Pool();
    pool.setPoolId("foo");
    pool.setDenomination(1000000);
    pool.setFeeValue(FEES_VALID);
    pool.setMustMixBalanceMin(1000102);
    pool.setMustMixBalanceMax(1010000);
    pool.setMinAnonymitySet(1);
    pool.setMixAnonymitySet(2);
    pool.setMinMustMix(1);

    byte[] feePayload = Utils.feePayloadShortToBytes(SCODE_FOO_PAYLOAD); // valid feePayload
    String feePaymentCode = feeValidationService.getFeePaymentCode();
    String feeAddress = "tb1q9fj036sha0mv25qm6ruk7l85xy2wy6qp853yx0";
    int feeIndex = 123456;

    Tx0Data tx0Data = new Tx0Data(feePaymentCode, 0, 1111, 100, feePayload, feeAddress, feeIndex);
    Tx0Preview tx0Preview = new Tx0Preview(tx0Data, 2, 1000102, 94998479, 4);
    Tx0 tx0 =
        new Tx0Service(whirlpoolWalletConfig)
            .tx0(
                spendFroms,
                depositWallet,
                premixWallet,
                postmixWallet,
                badBankWallet,
                new Tx0Config(),
                tx0Preview);

    WhirlpoolFeeData feeData = feeValidationService.decodeFeeData(tx0.getTx());
    Assert.assertEquals(0, feeData.getFeeIndice()); // feeIndice overriden by feePayload
    Assert.assertArrayEquals(feePayload, feeData.getFeePayload());

    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    Assert.assertTrue(feeValidationService.isValidTx0(tx0.getTx(), 1234, feeData, poolFee));
  }

  @Test
  public void isValidTx0_feePayloadInvalid() throws Exception {
    UnspentOutputWithKey spendFrom = testUtils.generateUnspentOutputWithKey(99000000, params);
    Collection<UnspentOutputWithKey> spendFroms = Lists.of(spendFrom);
    HD_Wallet bip84w =
        hdWalletFactory.restoreWallet(
            "all all all all all all all all all all all all", "test", 1, params);

    Bip84Wallet depositWallet =
        new Bip84Wallet(bip84w, 0, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet premixWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 2, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet postmixWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 4, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet badBankWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 3, new MemoryIndexHandler(), new MemoryIndexHandler());

    Pool pool = new Pool();
    pool.setPoolId("foo");
    pool.setDenomination(1000000);
    pool.setFeeValue(FEES_VALID);
    pool.setMustMixBalanceMin(1000102);
    pool.setMustMixBalanceMax(1010000);
    pool.setMinAnonymitySet(1);
    pool.setMixAnonymitySet(2);
    pool.setMinMustMix(1);
    List<Pool> poolItems = new ArrayList<>();
    poolItems.add(pool);

    String feePaymentCode = feeValidationService.getFeePaymentCode();
    byte[] feePayload = new byte[] {01, 23}; // invalid feePayload
    String feeAddress = "tb1q9fj036sha0mv25qm6ruk7l85xy2wy6qp853yx0";
    int feeIndex = 123456;

    Tx0Data tx0Data =
        new Tx0Data(feePaymentCode, 0, FEES_VALID, 0, feePayload, feeAddress, feeIndex);
    Tx0Preview tx0Preview = new Tx0Preview(tx0Data, 2, 1000102, 94024590, 4);

    Tx0 tx0 =
        new Tx0Service(whirlpoolWalletConfig)
            .tx0(
                spendFroms,
                depositWallet,
                premixWallet,
                postmixWallet,
                badBankWallet,
                new Tx0Config(),
                tx0Preview);

    WhirlpoolFeeData feeData = feeValidationService.decodeFeeData(tx0.getTx());
    Assert.assertEquals(0, feeData.getFeeIndice()); // feeIndice overriden by feePayload
    Assert.assertArrayEquals(feePayload, feeData.getFeePayload());

    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    Assert.assertFalse(feeValidationService.isValidTx0(tx0.getTx(), 1234, feeData, poolFee));
  }

  @Test
  public void isValidTx0_feePayload2() {
    // reject nofee when unknown feePayload
    String txid = "b3557587f87bcbd37e847a0fff0ded013b23026f153d85f28cb5d407d39ef2f3";
    PoolFee poolFee = new PoolFee(FEES_VALID, null);

    Transaction tx = getTx(txid);
    Assert.assertFalse(
        feeValidationService.isValidTx0(tx, 1234, feeValidationService.decodeFeeData(tx), poolFee));

    // accept when valid feePayload
    setScodeConfig("myscode", (short) 12345, 0, null);
    Assert.assertTrue(
        feeValidationService.isValidTx0(tx, 1234, feeValidationService.decodeFeeData(tx), poolFee));
  }

  @Test
  public void isValidTx0_noFeePayload() throws Exception {
    UnspentOutputWithKey spendFrom = testUtils.generateUnspentOutputWithKey(99000000, params);
    Collection<UnspentOutputWithKey> spendFroms = Lists.of(spendFrom);
    HD_Wallet bip84w =
        hdWalletFactory.restoreWallet(
            "all all all all all all all all all all all all", "test", 1, params);

    Bip84Wallet depositWallet =
        new Bip84Wallet(bip84w, 0, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet premixWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 2, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet postmixWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 4, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet badBankWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 3, new MemoryIndexHandler(), new MemoryIndexHandler());

    Pool pool = new Pool();
    pool.setPoolId("foo");
    pool.setDenomination(1000000);
    pool.setFeeValue(FEES_VALID);
    pool.setMustMixBalanceMin(1000102);
    pool.setMustMixBalanceMax(1010000);
    pool.setMinAnonymitySet(1);
    pool.setMixAnonymitySet(2);
    pool.setMinMustMix(1);
    List<Pool> poolItems = new ArrayList<>();
    poolItems.add(pool);

    String feePaymentCode = feeValidationService.getFeePaymentCode();
    byte[] feePayload = null; // no feePayload
    String feeAddress = "tb1qav0v5yvxlq0j70e3ygdsy04k6n6mndxl8733zu";

    int feeIndex = 123456;

    Tx0Data tx0Data =
        new Tx0Data(feePaymentCode, FEES_VALID, 0, 0, feePayload, feeAddress, feeIndex);
    Tx0Preview tx0Preview = new Tx0Preview(tx0Data, 2, 1000102, 94024590, 4);

    Tx0 tx0 =
        new Tx0Service(whirlpoolWalletConfig)
            .tx0(
                spendFroms,
                depositWallet,
                premixWallet,
                postmixWallet,
                badBankWallet,
                new Tx0Config(),
                tx0Preview);

    WhirlpoolFeeData feeData = feeValidationService.decodeFeeData(tx0.getTx());
    Assert.assertEquals(feeIndex, feeData.getFeeIndice());
    Assert.assertArrayEquals(feePayload, feeData.getFeePayload());

    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    Assert.assertTrue(feeValidationService.isValidTx0(tx0.getTx(), 1234, feeData, poolFee));
  }

  @Test
  public void isValidTx0_noFeePayload_invalidAddress() throws Exception {
    UnspentOutputWithKey spendFrom = testUtils.generateUnspentOutputWithKey(99000000, params);
    Collection<UnspentOutputWithKey> spendFroms = Lists.of(spendFrom);
    HD_Wallet bip84w =
        hdWalletFactory.restoreWallet(
            "all all all all all all all all all all all all", "test", 1, params);

    Bip84Wallet depositWallet =
        new Bip84Wallet(bip84w, 0, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet premixWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 2, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet postmixWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 4, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet badBankWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 3, new MemoryIndexHandler(), new MemoryIndexHandler());

    Pool pool = new Pool();
    pool.setPoolId("foo");
    pool.setDenomination(1000000);
    pool.setFeeValue(FEES_VALID);
    pool.setMustMixBalanceMin(1000102);
    pool.setMustMixBalanceMax(1010000);
    pool.setMinAnonymitySet(1);
    pool.setMixAnonymitySet(2);
    pool.setMinMustMix(1);
    List<Pool> poolItems = new ArrayList<>();
    poolItems.add(pool);

    String feePaymentCode = feeValidationService.getFeePaymentCode();
    byte[] feePayload = null; // no feePayload
    String feeAddress = "tb1q9fj036sha0mv25qm6ruk7l85xy2wy6qp853yx0"; // invalid address

    int feeIndex = 123456;

    Tx0Data tx0Data =
        new Tx0Data(feePaymentCode, FEES_VALID, 0, 0, feePayload, feeAddress, feeIndex);
    Tx0Preview tx0Preview = new Tx0Preview(tx0Data, 2, 1000102, 94024590, 4);

    Tx0 tx0 =
        new Tx0Service(whirlpoolWalletConfig)
            .tx0(
                spendFroms,
                depositWallet,
                premixWallet,
                postmixWallet,
                badBankWallet,
                new Tx0Config(),
                tx0Preview);

    WhirlpoolFeeData feeData = feeValidationService.decodeFeeData(tx0.getTx());
    Assert.assertEquals(feeIndex, feeData.getFeeIndice());
    Assert.assertArrayEquals(feePayload, feeData.getFeePayload());

    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    Assert.assertFalse(feeValidationService.isValidTx0(tx0.getTx(), 1234, feeData, poolFee));
  }

  @Test
  public void getFeePayloadByScode() throws Exception {
    long now = System.currentTimeMillis();
    Assert.assertEquals(
        0, (int) feeValidationService.getScodeConfigByScode(SCODE_FOO_0, now).getFeeValuePercent());
    Assert.assertEquals(
        25,
        (int) feeValidationService.getScodeConfigByScode(SCODE_BAR_25, now).getFeeValuePercent());
    Assert.assertEquals(
        50,
        (int) feeValidationService.getScodeConfigByScode(SCODE_MIN_50, now).getFeeValuePercent());
    Assert.assertEquals(
        80,
        (int) feeValidationService.getScodeConfigByScode(SCODE_MAX_80, now).getFeeValuePercent());
    // case non-sensitive
    Assert.assertEquals(
        80, (int) feeValidationService.getScodeConfigByScode("MaX", now).getFeeValuePercent());
    Assert.assertEquals(
        80, (int) feeValidationService.getScodeConfigByScode("max", now).getFeeValuePercent());
    Assert.assertEquals(
        80, (int) feeValidationService.getScodeConfigByScode("MAX", now).getFeeValuePercent());
    Assert.assertEquals(null, feeValidationService.getScodeConfigByScode("invalid", now));
  }

  @Test
  public void getScodeByFeePayload() throws Exception {
    Assert.assertEquals(
        SCODE_FOO_PAYLOAD,
        (short)
            feeValidationService
                .getScodeByFeePayload(Utils.feePayloadShortToBytes(SCODE_FOO_PAYLOAD))
                .getPayload());
    Assert.assertEquals(
        SCODE_BAR_PAYLOAD,
        (short)
            feeValidationService
                .getScodeByFeePayload(Utils.feePayloadShortToBytes(SCODE_BAR_PAYLOAD))
                .getPayload());
    Assert.assertEquals(
        SCODE_MIN_PAYLOAD,
        (short)
            feeValidationService
                .getScodeByFeePayload(Utils.feePayloadShortToBytes(SCODE_MIN_PAYLOAD))
                .getPayload());
    Assert.assertEquals(
        SCODE_MAX_PAYLOAD,
        (short)
            feeValidationService
                .getScodeByFeePayload(Utils.feePayloadShortToBytes(SCODE_MAX_PAYLOAD))
                .getPayload());

    Assert.assertEquals(
        null, feeValidationService.getScodeByFeePayload(Utils.feePayloadShortToBytes((short) 0)));
    Assert.assertEquals(
        null, feeValidationService.getScodeByFeePayload(Utils.feePayloadShortToBytes((short) -1)));
  }
}
