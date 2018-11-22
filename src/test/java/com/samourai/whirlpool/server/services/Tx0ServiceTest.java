package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class Tx0ServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final long FEES_VALID = 975000;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dbService.__reset();
    serverConfig.getSamouraiFees().setAmount(FEES_VALID);
  }

  @Test
  public void findSamouraiFeesXpubIndiceFromTx0() {

    BiFunction<String, Integer, Void> test =
        (String txid, Integer xpubIndiceExpected) -> {
          log.info("Test: " + txid + ", " + xpubIndiceExpected);
          RpcTransaction rpcTransaction =
              blockchainDataService
                  .getRpcTransaction(txid)
                  .orElseThrow(() -> new NoSuchElementException());
          Integer x = tx0Service.findSamouraiFeesIndice(rpcTransaction);
          Assert.assertEquals(xpubIndiceExpected, x);
          return null;
        };

    test.apply("cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187", 1);
    test.apply("7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16", null);
    test.apply("5369dfb71b36ed2b91ca43f388b869e617558165e4f8306b80857d88bdd624f2", null);
  }

  @Test
  public void computeSamouraiFeesAddress() {
    Assert.assertEquals(
        tx0Service.computeSamouraiFeesAddress(1), "tb1qz84ma37y3d759sdy7mvq3u4vsxlg2qahw3lm23");
    Assert.assertEquals(
        tx0Service.computeSamouraiFeesAddress(2), "tb1qk20n7cpza4eakn0vqyfwskdgpwwwy29l5ek93w");
    Assert.assertEquals(
        tx0Service.computeSamouraiFeesAddress(3), "tb1qs394xtk0cx8ls9laymdn3scdrss3c2c20gttdx");
  }

  @Test
  public void isTx0FeesPaid() throws Exception {
    String txid = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";

    // accept when paid exact fee
    Assert.assertTrue(doIsTx0FeesPaid(txid, FEES_VALID, 1));

    // accept when paid more than fee
    Assert.assertTrue(doIsTx0FeesPaid(txid, FEES_VALID - 1, 1));
    Assert.assertTrue(doIsTx0FeesPaid(txid, 1, 1));

    // reject when paid less than fee
    Assert.assertFalse(doIsTx0FeesPaid(txid, FEES_VALID + 1, 1));
    Assert.assertFalse(doIsTx0FeesPaid(txid, 1000000, 1));

    // reject when paid to wrong xpub indice
    Assert.assertFalse(doIsTx0FeesPaid(txid, FEES_VALID, 0));
    Assert.assertFalse(doIsTx0FeesPaid(txid, FEES_VALID, 2));
    Assert.assertFalse(doIsTx0FeesPaid(txid, FEES_VALID, 10));
  }

  private boolean doIsTx0FeesPaid(String txid, long minFees, int xpubIndice) {
    serverConfig.getSamouraiFees().setAmount(minFees);
    RpcTransaction rpcTransaction =
        blockchainDataService
            .getRpcTransaction(txid)
            .orElseThrow(() -> new NoSuchElementException());
    return tx0Service.isTx0FeesPaid(rpcTransaction, xpubIndice);
  }
}
