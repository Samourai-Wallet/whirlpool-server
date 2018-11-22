package com.samourai.whirlpool.server.services;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

import com.samourai.whirlpool.server.beans.rpc.RpcIn;
import com.samourai.whirlpool.server.beans.rpc.RpcOut;
import com.samourai.whirlpool.server.beans.rpc.RpcTransaction;
import com.samourai.whirlpool.server.integration.AbstractIntegrationTest;
import com.samourai.whirlpool.server.services.rpc.MockRpcClientServiceImpl;
import java.lang.invoke.MethodHandles;
import java.util.NoSuchElementException;
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
public class BlockchainDataServiceTest extends AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void getRpcTransaction_96cebec97115f59339a9053b6084aab5869adeefdbdbe974b74bfdbf3b8eaac3() {
    String txid = "96cebec97115f59339a9053b6084aab5869adeefdbdbe974b74bfdbf3b8eaac3";
    RpcTransaction tx =
        blockchainDataService
            .getRpcTransaction(txid)
            .orElseThrow(() -> new NoSuchElementException());
    Assert.assertEquals(MockRpcClientServiceImpl.MOCK_TX_CONFIRMATIONS, tx.getConfirmations());
    Assert.assertEquals(
        "96cebec97115f59339a9053b6084aab5869adeefdbdbe974b74bfdbf3b8eaac3", tx.getTxid());

    Assert.assertEquals(1, tx.getIns().size());
    assertRpcIn(
        tx.getIns().get(0), "acbd0c2b7ee85c128e1e56755de34ea1c822d16badc2d740b7824e8845a2c475", 1);

    Assert.assertEquals(2, tx.getOuts().size());
    assertRpcOut(
        tx.getOuts().get(0),
        txid,
        0,
        119471485,
        "n4DsYVKicuP2GrqWetrdFmYKe1gYTN8jwW",
        "76a914f90fdb0b607a45715ed8326a2180f4d338b242d588ac");
    assertRpcOut(
        tx.getOuts().get(1),
        txid,
        1,
        634252905,
        "msnNdvtqKLaYVxNyYjNQxVDqE9QVZCsbfm",
        "76a914868c11afa0e8dbe159b3eacc08ff017fba9d391988ac");
  }

  @Test
  public void getRpcTransaction_7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16() {
    String txid = "7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16";
    RpcTransaction tx =
        blockchainDataService
            .getRpcTransaction(txid)
            .orElseThrow(() -> new NoSuchElementException());
    Assert.assertEquals(MockRpcClientServiceImpl.MOCK_TX_CONFIRMATIONS, tx.getConfirmations());
    Assert.assertEquals(
        "7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16", tx.getTxid());

    Assert.assertEquals(3, tx.getIns().size());
    assertRpcIn(
        tx.getIns().get(0), "419b92f7de21733f531f0623c0f9b7ceaab396f894881bf4b6d193b27ffefc3d", 2);
    assertRpcIn(
        tx.getIns().get(1), "7c73ef7e21cd4c8d87c944f2d49f9110105d7bf1bd391b3ec2e345d290dc402b", 0);
    assertRpcIn(
        tx.getIns().get(2), "84da039c7509689e84c9b06033427d71936275729424f759d6da0a7ae63254c4", 2);

    Assert.assertEquals(4, tx.getOuts().size());
    assertRpcOut(
        tx.getOuts().get(0),
        txid,
        0,
        48318441,
        "2N587asHhA9WSE8L7aQXAnYkkdc1cDQdGHk",
        "a9148249408a629e70e42349addd3e36888a0ea1578287");
    assertRpcOut(
        tx.getOuts().get(1),
        txid,
        1,
        99994570,
        "mm5UkfQZgCmfZznNQ3HEnLdyDZ3xZRFCp6",
        "76a9143cff5d8af264dcbbc84bae87a209d3efce31734388ac");
    assertRpcOut(
        tx.getOuts().get(2),
        txid,
        2,
        100010000,
        "tb1qjvz9f9dud8qddg7fu559eztf7g78nnu4c3yclu",
        "001493045495bc69c0d6a3c9e5285c8969f23c79cf95");
    assertRpcOut(
        tx.getOuts().get(3),
        txid,
        3,
        100010000,
        "tb1q67vv48r7we84rp5g0u9nsxjskufzce5t2955cp",
        "0014d798ca9c7e764f5186887f0b381a50b7122c668b");
  }

  private void assertRpcOut(
      RpcOut rpcOut,
      String hash,
      long index,
      long value,
      String toAddress,
      String scriptPubKeyHex) {
    Assert.assertEquals(hash, rpcOut.getHash());
    Assert.assertEquals(index, rpcOut.getIndex());
    Assert.assertEquals(value, rpcOut.getValue());
    Assert.assertEquals(toAddress, rpcOut.getToAddress());
    Assert.assertEquals(scriptPubKeyHex, Utils.HEX.encode(rpcOut.getScriptPubKey()));
  }

  private void assertRpcIn(RpcIn rpcIn, String hash, long index) {
    Assert.assertEquals(hash, rpcIn.getOriginHash());
    Assert.assertEquals(index, rpcIn.getOriginIndex());
  }
}
