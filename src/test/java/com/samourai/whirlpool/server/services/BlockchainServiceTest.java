package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.RpcTransaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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
public class BlockchainServiceTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private BlockchainDataService blockchainDataService;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void checkInputPaidFees() throws Exception {
        RpcTransaction rpcTransaction = blockchainDataService.getRpcTransaction("cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187");

        Integer x = blockchainService.findSamouraiFeesXpubIndiceFromTx0(rpcTransaction);
        Assert.assertNotNull(x);

        blockchainService.checkTx0PaidFees(rpcTransaction, 3, 1, x);
    }

    /*
    @Test
    public void getTxOutPoint() throws Exception {
        String outHash = "aedsds";
        int outIdx = 0;
        TxOutPoint txOutPoint = blockchainService.getTxOutPoint(outHash, outIdx);
        Assert.assertEquals(0, txOutPoint.getIndex());
        Assert.assertEquals("abcd", txOutPoint.getHash());
        Assert.assertEquals(123L, txOutPoint.getValue());
    }

    @Test
    public void getToAddress() throws Exception {
        Assert.assertEquals("aaa", blockchainService.getToAddress(new TxOutPoint("aaaa", 0));
    }

    @Test
    public void checkSamouraiFeesPaid() throws Exception {
        final long minFees = 10000;
        Assert.assertFalse(blockchainService.checkSamouraiFeesPaid("aaaa", 0, minFees);
        Assert.assertTrue(blockchainService.checkSamouraiFeesPaid("aaaa", 1, minFees);
        Assert.assertTrue(blockchainService.checkSamouraiFeesPaid("aaaa", 0, 50);
        Assert.assertTrue(blockchainService.checkSamouraiFeesPaid("abc", 0, minFees);
    }*/


}