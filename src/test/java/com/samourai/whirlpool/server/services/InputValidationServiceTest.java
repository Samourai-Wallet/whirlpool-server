package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.whirlpool.server.beans.rpc.RpcOutWithTx;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import java.util.NoSuchElementException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class InputValidationServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final long FEES_VALID = 975000;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dbService.__reset();
    serverConfig.getSamouraiFees().setAmount(FEES_VALID);
  }

  @Test
  public void checkInput_invalid() throws Exception {
    // txid not in db
    String txid = "7d14d7d85eeda1efe7593d89cc8b61940c4a17b9390ae471577bbdc489c542eb";

    // invalid
    Assert.assertFalse(isWhirlpoolTx(txid, 1000000));

    // reject when invalid
    thrown.expect(IllegalInputException.class);
    thrown.expectMessage("Input rejected (not a premix or whirlpool input)");
    doCheckInput(txid, 0);
  }

  @Test
  public void checkInput_invalidDenomination() throws Exception {
    // register as valid whirlpool txid
    String txid = "ae97a4d646cf96f01f16d845f1b2be7ff1eaa013b8c957caa8514bba28336f13";
    long denomination = 1000000;
    try {
      dbService.saveMixTxid(txid, denomination + 1);
    } catch (Exception e) {
    } // ignore duplicate

    // reject when invalid denomination
    Assert.assertFalse(isWhirlpoolTx(txid, denomination)); // invalid
    thrown.expect(IllegalInputException.class);
    thrown.expectMessage("Input rejected (not a premix or whirlpool input)");
    doCheckInput(txid, 0);
  }

  @Test
  public void checkInput_valid() throws Exception {
    // register as valid whirlpool txid
    String txid = "ae97a4d646cf96f01f16d845f1b2be7ff1eaa013b8c957caa8514bba28336f13";
    long denomination = 1000000;
    try {
      dbService.saveMixTxid(txid, denomination);
    } catch (Exception e) {
    } // ignore duplicate

    // accept when valid
    Assert.assertTrue(isWhirlpoolTx(txid, denomination)); // valid
    Assert.assertTrue(doCheckInput(txid, 0)); // liquidity
  }

  private boolean isWhirlpoolTx(String utxoHash, long denomination) {
    RpcTransaction rpcTransaction =
        blockchainDataService
            .getRpcTransaction(utxoHash)
            .orElseThrow(() -> new NoSuchElementException());

    return inputValidationService.isWhirlpoolTx(rpcTransaction.getTx(), denomination);
  }

  @Test
  public void checkInput() throws Exception {
    String txid = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";

    // accept when valid mustMix, paid exact fee
    serverConfig.getSamouraiFees().setAmount(FEES_VALID);
    for (int i = 0; i < 8; i++) {
      Assert.assertFalse(doCheckInput(txid, i));
    }

    // accept when valid mustMix, paid more than fee
    serverConfig.getSamouraiFees().setAmount(FEES_VALID - 1);
    for (int i = 0; i < 8; i++) {
      Assert.assertFalse(doCheckInput(txid, i));
    }

    // reject when paid less than fee
    serverConfig.getSamouraiFees().setAmount(FEES_VALID + 1);
    for (int i = 0; i < 8; i++) {
      thrown.expect(IllegalInputException.class);
      thrown.expectMessage("Input rejected (invalid fee for tx0=" + txid + ", x=1)");
      doCheckInput(txid, i);
    }
  }

  private boolean doCheckInput(String utxoHash, long utxoIndex) throws IllegalInputException {
    RpcOutWithTx rpcOutWithTx =
        blockchainDataService
            .getRpcOutWithTx(utxoHash, utxoIndex)
            .orElseThrow(() -> new NoSuchElementException(utxoHash + "-" + utxoIndex));
    boolean isLiquidity = inputValidationService.checkInput(rpcOutWithTx);
    return isLiquidity;
  }
}
