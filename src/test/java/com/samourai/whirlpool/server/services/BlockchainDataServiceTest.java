package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.RpcOut;
import com.samourai.whirlpool.server.beans.RpcTransaction;
import org.bitcoinj.core.Utils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.invoke.MethodHandles;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class BlockchainDataServiceTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    private BlockchainDataService blockchainDataService;

    @Test
    public void getRpcTransaction() throws Exception {
        String txid = "96cebec97115f59339a9053b6084aab5869adeefdbdbe974b74bfdbf3b8eaac3";
        RpcTransaction rpcTransaction = blockchainDataService.getRpcTransaction(txid);
        Assert.assertTrue(rpcTransaction.getConfirmations() > 1063000);
        Assert.assertEquals("96cebec97115f59339a9053b6084aab5869adeefdbdbe974b74bfdbf3b8eaac3", rpcTransaction.getHash());
        Assert.assertEquals(2, rpcTransaction.getOuts().size());

        RpcOut rpcOut = rpcTransaction.getOuts().get(0);
        Assert.assertEquals(0, rpcOut.getIndex());
        Assert.assertEquals(119471485, rpcOut.getValue());
        Assert.assertEquals("3736613931346639306664623062363037613435373135656438333236613231383066346433333862323432643538386163", Utils.HEX.encode(rpcOut.getScriptPubKey()));

        rpcOut = rpcTransaction.getOuts().get(1);
        Assert.assertEquals(1, rpcOut.getIndex());
        Assert.assertEquals(634252905, rpcOut.getValue());
        Assert.assertEquals("3736613931343836386331316166613065386462653135396233656163633038666630313766626139643339313938386163", Utils.HEX.encode(rpcOut.getScriptPubKey()));
    }


}