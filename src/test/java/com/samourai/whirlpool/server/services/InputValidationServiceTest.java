package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.server.beans.PoolFee;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import java.util.NoSuchElementException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class InputValidationServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final long FEES_VALID = 975000;

  @BeforeEach
  public void beforeEach() {
    dbService.__reset();
  }

  @Test
  public void checkInput_invalid() throws Exception {
    // txid not in db
    String txid = "7d14d7d85eeda1efe7593d89cc8b61940c4a17b9390ae471577bbdc489c542eb";

    // invalid
    Assert.assertFalse(hasMixTxid(txid, 1000000));

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
    Assert.assertFalse(hasMixTxid(txid, denomination)); // invalid
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
    Assert.assertTrue(hasMixTxid(txid, denomination)); // valid
    Assert.assertTrue(doCheckInput(txid, 0)); // liquidity
  }

  private boolean hasMixTxid(String utxoHash, long denomination) {
    return dbService.hasMixTxid(utxoHash, denomination);
  }

  @Test
  public void checkInput_noFeePayload() throws Exception {
    // register as valid whirlpool txid
    String txid = "6588946af1d9d92b402fd672360fd12217abfaf6382ce644d358e8174781f0ce";
    long FEES_VALID_TX = 50000;

    // accept when valid mustMix, paid exact fee
    PoolFee poolFee = new PoolFee(FEES_VALID_TX, null);
    for (int i = 2; i < 8; i++) {
      Assert.assertFalse(doCheckInput(txid, i, poolFee));
    }

    // reject when valid mustMix, paid more than fee
    poolFee = new PoolFee(FEES_VALID_TX - 1, null);
    for (int i = 2; i < 8; i++) {
      thrown.expect(IllegalInputException.class);
      thrown.expectMessage(
          "Input rejected (invalid fee for tx0=" + txid + ", x=0, feePayloadHex=null)");
      doCheckInput(txid, i, poolFee);
    }

    // reject when paid less than fee
    poolFee = new PoolFee(FEES_VALID_TX + 1, null);
    for (int i = 2; i < 8; i++) {
      thrown.expect(IllegalInputException.class);
      thrown.expectMessage(
          "Input rejected (invalid fee for tx0=" + txid + ", x=0, feePayloadHex=null)");
      doCheckInput(txid, i, poolFee);
    }
  }

  @Test
  public void checkInput_noFeePayload_invalidAddress() throws Exception {
    // register as valid whirlpool txid
    String txid = "7aa680b658cf26aa94944875d31dcd60db204e1e746dfd36cfcd677494ca89a4";
    long FEES_VALID_TX = 50000;

    // valid mustMix, paid exact fee
    PoolFee poolFee = new PoolFee(FEES_VALID_TX, null);
    for (int i = 2; i < 8; i++) {
      // invalid fee address
      thrown.expect(IllegalInputException.class);
      thrown.expectMessage(
          "Input rejected (invalid fee for tx0=" + txid + ", x=643, feePayloadHex=null)");
      doCheckInput(txid, i, poolFee);
    }
  }

  @Test
  public void checkInput_feePayload_invalid() throws Exception {
    // reject nofee when unknown feePayload
    thrown.expect(IllegalInputException.class);
    thrown.expectMessage(
        "Input rejected (invalid fee for tx0=b3557587f87bcbd37e847a0fff0ded013b23026f153d85f28cb5d407d39ef2f3, x=11, feePayloadHex=3039)");
    doCheckInput("b3557587f87bcbd37e847a0fff0ded013b23026f153d85f28cb5d407d39ef2f3", 2);
  }

  @Test
  public void checkInput_feePayload_valid() throws Exception {
    // accept when valid feePayload
    setScodeConfig("myscode", (short) 12345, 0, null);
    doCheckInput("b3557587f87bcbd37e847a0fff0ded013b23026f153d85f28cb5d407d39ef2f3", 2);
  }

  private boolean doCheckInput(String utxoHash, long utxoIndex) throws NotifiableException {
    PoolFee poolFee = new PoolFee(FEES_VALID, null);
    return doCheckInput(utxoHash, utxoIndex, poolFee);
  }

  private boolean doCheckInput(String utxoHash, long utxoIndex, PoolFee poolFee)
      throws NotifiableException {
    RpcTransaction rpcTransaction =
        blockchainDataService
            .getRpcTransaction(utxoHash)
            .orElseThrow(() -> new NoSuchElementException(utxoHash + "-" + utxoIndex));
    long inputValue = rpcTransaction.getTx().getOutput(utxoIndex).getValue().getValue();
    boolean hasMixTxid = hasMixTxid(utxoHash, inputValue);
    boolean isLiquidity =
        inputValidationService.checkInputProvenance(
            rpcTransaction, inputValue, poolFee, hasMixTxid);
    return isLiquidity;
  }
}
