package com.samourai.whirlpool.server.utils;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import org.bouncycastle.util.encoders.Hex;
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
    Assert.assertEquals("0001", Hex.toHexString(Utils.feePayloadShortToBytes((short) 1)));
    Assert.assertEquals("04d2", Hex.toHexString(Utils.feePayloadShortToBytes((short) 1234)));
    Assert.assertEquals("162e", Hex.toHexString(Utils.feePayloadShortToBytes((short) 5678)));
  }

  @Test
  public void feePayloadBytesToShort() throws Exception {
    Assert.assertEquals(1, Utils.feePayloadBytesToShort(Hex.decode("0001")));
    Assert.assertEquals(1234, Utils.feePayloadBytesToShort(Hex.decode("04d2")));
    Assert.assertEquals(5678, Utils.feePayloadBytesToShort(Hex.decode("162e")));
  }
}
