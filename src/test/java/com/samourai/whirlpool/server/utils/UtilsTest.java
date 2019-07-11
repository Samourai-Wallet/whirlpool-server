package com.samourai.whirlpool.server.utils;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class UtilsTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void feePayload() throws Exception {
    short feePayload = 1234;

    Assert.assertEquals(
        feePayload, Utils.feePayloadBytesToShort(Utils.feePayloadShortToBytes(feePayload)));
  }

  @Test
  public void feePayloadShortToBytes() throws Exception {
    Assert.assertEquals(
        "00000000 00000001", Utils.bytesToBinaryString(Utils.feePayloadShortToBytes((short) 1)));
    Assert.assertEquals(
        "00000100 11010010", Utils.bytesToBinaryString(Utils.feePayloadShortToBytes((short) 1234)));
    Assert.assertEquals(
        "00010110 00101110", Utils.bytesToBinaryString(Utils.feePayloadShortToBytes((short) 5678)));

    Assert.assertEquals(
        "11111111 11111011", Utils.bytesToBinaryString(Utils.feePayloadShortToBytes((short) -5)));

    // min
    Assert.assertEquals(
        "10000000 00000000",
        Utils.bytesToBinaryString(Utils.feePayloadShortToBytes((short) -32768)));

    // max
    Assert.assertEquals(
        "01111111 11111111",
        Utils.bytesToBinaryString(Utils.feePayloadShortToBytes((short) 32767)));
  }

  @Test
  public void feePayloadBytesToShort() throws Exception {
    Assert.assertEquals(
        1, Utils.feePayloadBytesToShort(Utils.bytesFromBinaryString("00000000 00000001")));
    Assert.assertEquals(
        1234, Utils.feePayloadBytesToShort(Utils.bytesFromBinaryString("00000100 11010010")));
    Assert.assertEquals(
        5678, Utils.feePayloadBytesToShort(Utils.bytesFromBinaryString("00010110 00101110")));

    Assert.assertEquals(
        -5, Utils.feePayloadBytesToShort(Utils.bytesFromBinaryString("11111111 11111011")));
    Assert.assertEquals(
        -32768, Utils.feePayloadBytesToShort(Utils.bytesFromBinaryString("10000000 00000000")));
    Assert.assertEquals(
        32767, Utils.feePayloadBytesToShort(Utils.bytesFromBinaryString("01111111 11111111")));
  }

  @Test
  public void bytesToBinaryString() {
    Assert.assertEquals("00000000", Utils.bytesToBinaryString(new byte[] {0}));
    Assert.assertEquals("00000001", Utils.bytesToBinaryString(new byte[] {1}));
    Assert.assertEquals("00000001 00000000", Utils.bytesToBinaryString(new byte[] {1, 0}));
    Assert.assertEquals(
        "00000001 00001010 00001000 00001111",
        Utils.bytesToBinaryString(new byte[] {1, 10, 8, 15}));
  }

  @Test
  public void bytesFromBinaryString() {
    Assert.assertArrayEquals(new byte[] {0}, Utils.bytesFromBinaryString("00000000"));
    Assert.assertArrayEquals(new byte[] {1}, Utils.bytesFromBinaryString("00000001"));
    Assert.assertArrayEquals(new byte[] {1, 0}, Utils.bytesFromBinaryString("00000001 00000000"));
    Assert.assertArrayEquals(
        new byte[] {1, 10, 8, 15},
        Utils.bytesFromBinaryString("00000001 00001010 00001000 00001111"));
  }

  @Test
  public void computeBlameIdentitifer_mustmix() {
    ConfirmedInput confirmedInput =
        testUtils.computeConfirmedInput(
            "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187", 2, false);

    // mustmix => should ban TX0
    String expected = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";
    String actual = Utils.computeBlameIdentitifer(confirmedInput);
    Assert.assertEquals(actual, actual);
  }

  @Test
  public void computeBlameIdentitifer_liquidity() {
    ConfirmedInput confirmedInput =
        testUtils.computeConfirmedInput(
            "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187", 2, true);

    // liquidity => should ban UTXO
    String expected = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187:2";
    String actual = Utils.computeBlameIdentitifer(confirmedInput);
    Assert.assertEquals(actual, actual);
  }
}
