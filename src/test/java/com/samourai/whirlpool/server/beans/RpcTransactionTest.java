package com.samourai.whirlpool.server.beans;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.whirlpool.server.beans.rpc.RpcIn;
import com.samourai.whirlpool.server.beans.rpc.RpcOut;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.services.rpc.RpcRawTransactionResponse;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class RpcTransactionTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final Map<String, String> expectedHexs = new HashMap<>();
  private static final Map<String, String> expectedJson = new HashMap<>();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    expectedHexs.put(
        "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187",
        "01000000000101d9a61885250550a0f074e1866f5dbc4bd96224b2af2f75b70fbfc8312de7cd0b0000000000ffffffff080000000000000000066a040000000198e00e000000000016001411ebbec7c48b7d42c1a4f6d808f2ac81be8503b7689a9800000000001600142a64f8ea17ebf6c5501bd0f96f7cf43114e26801689a9800000000001600149747d7abc760e033a19d477d2091582f76b4308b689a9800000000001600149c1ffd729a95ee034e8efc55e10226ec17ae87a8689a980000000000160014ea6d4e82441d3e99b21197964b5e814ad2e6430c689a980000000000160014f9db48da4dea3d5304e8c6516cd229f20a0188999c806e0100000000160014d08a7c707572ace8fcecbc6210e31c177bdf803e02483045022100adee6cc97538f29fbe64e3ee10300bb2222327306b91c749f0f572949385cb5102200f76be99e4cf8f61454acddb0ffdfbec7dde288d49e7cde52cd940d4321f7083012102d0f240f307e6b32f94cf39e61dfdc8570cb29adab92e8a24d5a48cfe3eaf70c100000000");
    expectedHexs.put(
        "7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16",
        "010000000001033dfcfe7fb293d1b6f41b8894f896b3aaceb7f9c023061f533f7321def7929b41020000006b483045022100fd69af97109ff7f5b6aa656e8401d1f00d136ec2577d20b01b2f5154ef41f5420220205a62c372bec510caf800b2a996cc7bf0f52fc0d17fc871dd5c911bb495754501210206e398443b1468e028ef785281fdb39565d8f5dd5e29b9b8cf3fe6efb93062bafdffffff2b40dc90d245e3c23e1b39bdf17b5d1010919fd4f244c9878d4ccd217eef737c000000001716001485cafa3f554071a35f571027b8834b33b82ec056fdffffffc45432e67a0adad659f7249472756293717d423360b0c9849e6809759c03da84020000006a4730440220024e6febc89c6e313f8b297f1aec87ff057128c253f7e352b1635c3c88cf504002206c51f50d1dd4fa4c689c24d2c2bb35ee1d5cb2f99602e0aea9ffdc37092c062b012102632f214738f6f7708e201f6a299d6351eb87caf6b86ce94187ea39c98d18a60bfdffffff04e947e1020000000017a9148249408a629e70e42349addd3e36888a0ea1578287cacbf505000000001976a9143cff5d8af264dcbbc84bae87a209d3efce31734388ac1008f6050000000016001493045495bc69c0d6a3c9e5285c8969f23c79cf951008f60500000000160014d798ca9c7e764f5186887f0b381a50b7122c668b00024830450221009a870dec25f0794b91e594f21a88ea68e9ae9eb8824e54ce2cedd9c9ebe2ed7202203de75af50fe318738ca0e189835b66a5bc3f392d4a307a8c2cd29f780b19e57801210376edd2a70c6eba6b32f35965db0ed9c5502c0876b0600e754f9da9511ab80bca0000000000");
    expectedHexs.put(
        "96cebec97115f59339a9053b6084aab5869adeefdbdbe974b74bfdbf3b8eaac3",
        "010000000175c4a245884e82b740d7c2ad6bd122c8a14ee35d75561e8e125ce87e2b0cbdac010000006b483045022100cea6b68f65775d6f28bb6c592433517d5d5284045607160a1c554ca52b257b66022026b785d5080e9149e37f7e3ef5e5bf78d30d2512d066d416b300a4be6b1527c70121024ca44f264eb1c3cdbdb7108ae15f42969ce02bfe48bac94f9845535dcc33ac86ffffffff027dfd1e07000000001976a914f90fdb0b607a45715ed8326a2180f4d338b242d588ac69eecd25000000001976a914868c11afa0e8dbe159b3eacc08ff017fba9d391988ac00000000");

    expectedJson.put(
        "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187",
        "{\"txid\":\"cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187\",\"confirmations\":1234,\"ins\":[{\"originHash\":\"0bcde72d31c8bf0fb7752fafb22462d94bbc5d6f86e174f0a05005258518a6d9\",\"originIndex\":0}],\"outs\":[{\"hash\":\"cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187\",\"index\":0,\"value\":0,\"scriptPubKey\":\"agQAAAAB\",\"toAddress\":null},{\"hash\":\"cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187\",\"index\":1,\"value\":975000,\"scriptPubKey\":\"ABQR677HxIt9QsGk9tgI8qyBvoUDtw==\",\"toAddress\":\"tb1qz84ma37y3d759sdy7mvq3u4vsxlg2qahw3lm23\"},{\"hash\":\"cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187\",\"index\":2,\"value\":10001000,\"scriptPubKey\":\"ABQqZPjqF+v2xVAb0PlvfPQxFOJoAQ==\",\"toAddress\":\"tb1q9fj036sha0mv25qm6ruk7l85xy2wy6qp853yx0\"},{\"hash\":\"cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187\",\"index\":3,\"value\":10001000,\"scriptPubKey\":\"ABSXR9erx2DgM6GdR30gkVgvdrQwiw==\",\"toAddress\":\"tb1qjara0278vrsr8gvaga7jpy2c9amtgvytr44xym\"},{\"hash\":\"cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187\",\"index\":4,\"value\":10001000,\"scriptPubKey\":\"ABScH/1ympXuA06O/FXhAibsF66HqA==\",\"toAddress\":\"tb1qns0l6u56jhhqxn5wl327zq3xast6apagm6xudc\"},{\"hash\":\"cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187\",\"index\":5,\"value\":10001000,\"scriptPubKey\":\"ABTqbU6CRB0+mbIRl5ZLXoFK0uZDDA==\",\"toAddress\":\"tb1qafk5aqjyr5lfnvs3j7tykh5pftfwvscvar3307\"},{\"hash\":\"cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187\",\"index\":6,\"value\":10001000,\"scriptPubKey\":\"ABT520jaTeo9UwToxlFs0inyCgGImQ==\",\"toAddress\":\"tb1ql8d53kjdag74xp8gcegke53f7g9qrzyem8ca62\"},{\"hash\":\"cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187\",\"index\":7,\"value\":24019100,\"scriptPubKey\":\"ABTQinxwdXKs6PzsvGIQ4xwXe9+APg==\",\"toAddress\":\"tb1q6z98cur4w2kw3l8vh33ppccuzaaalqp7s4plwy\"}]}");
    expectedJson.put(
        "7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16",
        "{\"txid\":\"7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16\",\"confirmations\":1234,\"ins\":[{\"originHash\":\"419b92f7de21733f531f0623c0f9b7ceaab396f894881bf4b6d193b27ffefc3d\",\"originIndex\":2},{\"originHash\":\"7c73ef7e21cd4c8d87c944f2d49f9110105d7bf1bd391b3ec2e345d290dc402b\",\"originIndex\":0},{\"originHash\":\"84da039c7509689e84c9b06033427d71936275729424f759d6da0a7ae63254c4\",\"originIndex\":2}],\"outs\":[{\"hash\":\"7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16\",\"index\":0,\"value\":48318441,\"scriptPubKey\":\"qRSCSUCKYp5w5CNJrd0+NoiKDqFXgoc=\",\"toAddress\":\"2N587asHhA9WSE8L7aQXAnYkkdc1cDQdGHk\"},{\"hash\":\"7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16\",\"index\":1,\"value\":99994570,\"scriptPubKey\":\"dqkUPP9divJk3LvIS66HognT784xc0OIrA==\",\"toAddress\":\"mm5UkfQZgCmfZznNQ3HEnLdyDZ3xZRFCp6\"},{\"hash\":\"7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16\",\"index\":2,\"value\":100010000,\"scriptPubKey\":\"ABSTBFSVvGnA1qPJ5ShciWnyPHnPlQ==\",\"toAddress\":\"tb1qjvz9f9dud8qddg7fu559eztf7g78nnu4c3yclu\"},{\"hash\":\"7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16\",\"index\":3,\"value\":100010000,\"scriptPubKey\":\"ABTXmMqcfnZPUYaIfws4GlC3Eixmiw==\",\"toAddress\":\"tb1q67vv48r7we84rp5g0u9nsxjskufzce5t2955cp\"}]}");
    expectedJson.put(
        "96cebec97115f59339a9053b6084aab5869adeefdbdbe974b74bfdbf3b8eaac3",
        "{\"txid\":\"96cebec97115f59339a9053b6084aab5869adeefdbdbe974b74bfdbf3b8eaac3\",\"confirmations\":1234,\"ins\":[{\"originHash\":\"acbd0c2b7ee85c128e1e56755de34ea1c822d16badc2d740b7824e8845a2c475\",\"originIndex\":1}],\"outs\":[{\"hash\":\"96cebec97115f59339a9053b6084aab5869adeefdbdbe974b74bfdbf3b8eaac3\",\"index\":0,\"value\":119471485,\"scriptPubKey\":\"dqkU+Q/bC2B6RXFe2DJqIYD00ziyQtWIrA==\",\"toAddress\":\"n4DsYVKicuP2GrqWetrdFmYKe1gYTN8jwW\"},{\"hash\":\"96cebec97115f59339a9053b6084aab5869adeefdbdbe974b74bfdbf3b8eaac3\",\"index\":1,\"value\":634252905,\"scriptPubKey\":\"dqkUhowRr6Do2+FZs+rMCP8Bf7qdORmIrA==\",\"toAddress\":\"msnNdvtqKLaYVxNyYjNQxVDqE9QVZCsbfm\"}]}");
  }

  @Test
  public void testInstanciate() throws Exception {
    int CONFIRMATIONS = 1234;
    for (Map.Entry<String, String> entry : expectedHexs.entrySet()) {
      String txid = entry.getKey();
      String txhex = entry.getValue();

      RpcRawTransactionResponse rawTxResponse = new RpcRawTransactionResponse(txhex, CONFIRMATIONS);

      // TEST
      RpcTransaction rpcTransaction =
          new RpcTransaction(rawTxResponse, cryptoService.getNetworkParameters(), bech32Util);

      // VERIFY
      Assert.assertEquals(txid, rpcTransaction.getTxid());
      Assert.assertEquals(CONFIRMATIONS, rpcTransaction.getConfirmations());

      // verify RpcTransaction structure
      String expectedRpcTransactionJson = expectedJson.get(txid);
      String rpcTransactionJson =
          com.samourai.whirlpool.server.utils.Utils.toJsonString(rpcTransaction);
      Assert.assertEquals(expectedRpcTransactionJson, rpcTransactionJson);
    }
  }

  // make sure RpcTransaction <> bitcoinj transformations doesn't lose anything
  @Test
  public void testBitcoinj() throws Exception {
    int CONFIRMATIONS = 123;
    for (Map.Entry<String, String> entry : expectedHexs.entrySet()) {
      String txid = entry.getKey();
      String txhex = entry.getValue();

      // TEST
      Transaction tx =
          new Transaction(cryptoService.getNetworkParameters(), Utils.HEX.decode(txhex));
      log.info("txid=" + txid + ":\n" + tx.toString());

      // VERIFY
      Assert.assertEquals(txid, tx.getHashAsString());
      Assert.assertEquals(txhex, Utils.HEX.encode(tx.bitcoinSerialize()));

      // verify structure
      RpcRawTransactionResponse rawTxResponse = new RpcRawTransactionResponse(txhex, CONFIRMATIONS);
      RpcTransaction rpcTransaction =
          new RpcTransaction(rawTxResponse, cryptoService.getNetworkParameters(), bech32Util);

      Assert.assertEquals(tx.getHashAsString(), rpcTransaction.getTxid());

      Assert.assertEquals(tx.getInputs().size(), rpcTransaction.getIns().size());
      int inputIndex = 0;
      for (RpcIn rpcIn : rpcTransaction.getIns()) {
        TransactionInput txIn = tx.getInput(inputIndex);
        Assert.assertEquals(rpcIn.getOriginIndex(), txIn.getOutpoint().getIndex());
        Assert.assertEquals(rpcIn.getOriginHash(), txIn.getOutpoint().getHash().toString());
        inputIndex++;
      }

      Assert.assertEquals(tx.getOutputs().size(), rpcTransaction.getOuts().size());
      for (RpcOut rpcOut : rpcTransaction.getOuts()) {
        TransactionOutput txOut = tx.getOutput(rpcOut.getIndex());
        Assert.assertEquals(rpcOut.getIndex(), txOut.getIndex());
        Assert.assertEquals(rpcOut.getHash(), txid);
        Assert.assertEquals(rpcOut.getValue(), txOut.getValue().getValue());

        String toAddress =
            com.samourai.whirlpool.server.utils.Utils.getToAddressBech32(txOut, bech32Util, params);
        Assert.assertEquals(rpcOut.getToAddress(), toAddress);

        Assert.assertArrayEquals(rpcOut.getScriptPubKey(), txOut.getScriptPubKey().getProgram());
      }

      Assert.assertEquals(rawTxResponse.getConfirmations(), rpcTransaction.getConfirmations());
    }
  }
}
