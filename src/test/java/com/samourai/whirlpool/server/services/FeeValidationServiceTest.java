package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.tx0.Tx0Service;
import com.samourai.whirlpool.client.utils.Bip84Wallet;
import com.samourai.whirlpool.client.utils.indexHandler.MemoryIndexHandler;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.TransactionOutPoint;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class FeeValidationServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final long FEES_VALID = 975000;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dbService.__reset();
    serverConfig.getSamouraiFees().setAmount(FEES_VALID);
  }

  @Test
  public void findFeeIndice() {

    BiFunction<String, Integer, Void> test =
        (String txid, Integer xpubIndiceExpected) -> {
          log.info("Test: " + txid + ", " + xpubIndiceExpected);
          RpcTransaction rpcTransaction =
              blockchainDataService
                  .getRpcTransaction(txid)
                  .orElseThrow(() -> new NoSuchElementException());
          Integer x = feeValidationService.findFeeIndice(rpcTransaction.getTx());
          Assert.assertEquals(xpubIndiceExpected, x);
          return null;
        };

    test.apply("cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187", 1);
    test.apply("7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16", null);
    test.apply("5369dfb71b36ed2b91ca43f388b869e617558165e4f8306b80857d88bdd624f2", null);
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
  public void isTx0FeePaid() throws Exception {
    String txid = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";

    // accept when paid exact fee
    Assert.assertTrue(doIsTx0FeePaid(txid, FEES_VALID, 1));

    // accept when paid more than fee
    Assert.assertTrue(doIsTx0FeePaid(txid, FEES_VALID - 1, 1));
    Assert.assertTrue(doIsTx0FeePaid(txid, 1, 1));

    // reject when paid less than fee
    Assert.assertFalse(doIsTx0FeePaid(txid, FEES_VALID + 1, 1));
    Assert.assertFalse(doIsTx0FeePaid(txid, 1000000, 1));

    // reject when paid to wrong xpub indice
    Assert.assertFalse(doIsTx0FeePaid(txid, FEES_VALID, 0));
    Assert.assertFalse(doIsTx0FeePaid(txid, FEES_VALID, 2));
    Assert.assertFalse(doIsTx0FeePaid(txid, FEES_VALID, 10));
  }

  private boolean doIsTx0FeePaid(String txid, long minFees, int xpubIndice) {
    serverConfig.getSamouraiFees().setAmount(minFees);
    RpcTransaction rpcTransaction =
        blockchainDataService
            .getRpcTransaction(txid)
            .orElseThrow(() -> new NoSuchElementException());
    return feeValidationService.isTx0FeePaid(rpcTransaction.getTx(), xpubIndice);
  }

  @Test
  public void isTx0FeePaidClient() throws Exception {
    ECKey input0Key = new ECKey();
    String input0OutPointAddress = new SegwitAddress(input0Key, params).getBech32AsString();
    TransactionOutPoint input0OutPoint =
        cryptoTestUtil.generateTransactionOutPoint(input0OutPointAddress, 99000000, params);
    HD_Wallet bip84w =
        hdWalletFactory.restoreWallet(
            "all all all all all all all all all all all all", "test", 1, params);
    Bip84Wallet depositAndPremixWallet =
        new Bip84Wallet(bip84w, Integer.MAX_VALUE, new MemoryIndexHandler());

    String xpubSamouraiFee = serverConfig.getSamouraiFees().getXpub();
    String feePaymentCode = feeValidationService.getFeePaymentCode();
    int feeIndice = 123456;
    Tx0 tx0 =
        new Tx0Service(params)
            .tx0(
                input0Key.getPrivKeyBytes(),
                input0OutPoint,
                4,
                depositAndPremixWallet,
                1000000,
                300,
                xpubSamouraiFee,
                FEES_VALID,
                feePaymentCode,
                feeIndice);

    int xpubIndice = feeValidationService.findFeeIndice(tx0.getTx());
    Assert.assertEquals(feeIndice, xpubIndice);
    Assert.assertTrue(feeValidationService.isTx0FeePaid(tx0.getTx(), xpubIndice));
  }
}
