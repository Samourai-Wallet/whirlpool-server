package com.samourai.whirlpool.server.services.rpc;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class MockRpcClientServiceImplTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void mock() throws Exception {
    Map<String, String> expectedHexs = new HashMap<>();
    expectedHexs.put(
        "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187",
        "01000000000101d9a61885250550a0f074e1866f5dbc4bd96224b2af2f75b70fbfc8312de7cd0b0000000000ffffffff080000000000000000066a040000000198e00e000000000016001411ebbec7c48b7d42c1a4f6d808f2ac81be8503b7689a9800000000001600142a64f8ea17ebf6c5501bd0f96f7cf43114e26801689a9800000000001600149747d7abc760e033a19d477d2091582f76b4308b689a9800000000001600149c1ffd729a95ee034e8efc55e10226ec17ae87a8689a980000000000160014ea6d4e82441d3e99b21197964b5e814ad2e6430c689a980000000000160014f9db48da4dea3d5304e8c6516cd229f20a0188999c806e0100000000160014d08a7c707572ace8fcecbc6210e31c177bdf803e02483045022100adee6cc97538f29fbe64e3ee10300bb2222327306b91c749f0f572949385cb5102200f76be99e4cf8f61454acddb0ffdfbec7dde288d49e7cde52cd940d4321f7083012102d0f240f307e6b32f94cf39e61dfdc8570cb29adab92e8a24d5a48cfe3eaf70c100000000");
    expectedHexs.put(
        "7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16",
        "010000000001033dfcfe7fb293d1b6f41b8894f896b3aaceb7f9c023061f533f7321def7929b41020000006b483045022100fd69af97109ff7f5b6aa656e8401d1f00d136ec2577d20b01b2f5154ef41f5420220205a62c372bec510caf800b2a996cc7bf0f52fc0d17fc871dd5c911bb495754501210206e398443b1468e028ef785281fdb39565d8f5dd5e29b9b8cf3fe6efb93062bafdffffff2b40dc90d245e3c23e1b39bdf17b5d1010919fd4f244c9878d4ccd217eef737c000000001716001485cafa3f554071a35f571027b8834b33b82ec056fdffffffc45432e67a0adad659f7249472756293717d423360b0c9849e6809759c03da84020000006a4730440220024e6febc89c6e313f8b297f1aec87ff057128c253f7e352b1635c3c88cf504002206c51f50d1dd4fa4c689c24d2c2bb35ee1d5cb2f99602e0aea9ffdc37092c062b012102632f214738f6f7708e201f6a299d6351eb87caf6b86ce94187ea39c98d18a60bfdffffff04e947e1020000000017a9148249408a629e70e42349addd3e36888a0ea1578287cacbf505000000001976a9143cff5d8af264dcbbc84bae87a209d3efce31734388ac1008f6050000000016001493045495bc69c0d6a3c9e5285c8969f23c79cf951008f60500000000160014d798ca9c7e764f5186887f0b381a50b7122c668b00024830450221009a870dec25f0794b91e594f21a88ea68e9ae9eb8824e54ce2cedd9c9ebe2ed7202203de75af50fe318738ca0e189835b66a5bc3f392d4a307a8c2cd29f780b19e57801210376edd2a70c6eba6b32f35965db0ed9c5502c0876b0600e754f9da9511ab80bca0000000000");

    for (Map.Entry<String, String> entry : expectedHexs.entrySet()) {
      String txid = entry.getKey();
      String txhex = entry.getValue();
      int CONFIRMATIONS = 1234;

      // TEST
      rpcClientService.mock(txid, txhex, CONFIRMATIONS);

      // VERIFY
      RpcRawTransactionResponse rawTxResponse = rpcClientService.getRawTransaction(txid).get();
      Assert.assertEquals(txhex, rawTxResponse.getHex());
      Assert.assertEquals(rawTxResponse.getConfirmations(), CONFIRMATIONS);
    }
  }
}
