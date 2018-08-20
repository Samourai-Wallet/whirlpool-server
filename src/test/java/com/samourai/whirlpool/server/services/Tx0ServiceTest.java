package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.RpcOut;
import com.samourai.whirlpool.server.beans.RpcTransaction;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.utils.Utils;
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
import java.util.function.BiFunction;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class Tx0ServiceTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    private BlockchainDataService blockchainDataService;

    @Autowired
    private Tx0Service tx0Service;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void findSamouraiFeesXpubIndiceFromTx0() {

        BiFunction<String, Integer, Void> test = (String txid, Integer xpubIndiceExpected) -> {
            log.info("Test: "+txid+", "+xpubIndiceExpected);
            RpcTransaction rpcTransaction = blockchainDataService.getRpcTransaction(txid);
            Integer x = tx0Service.findSamouraiFeesXpubIndiceFromTx0(rpcTransaction);
            Assert.assertEquals(xpubIndiceExpected, x);
            return null;
        };

        test.apply("cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187", 1);
        test.apply("7ea75da574ebabf8d17979615b059ab53aae3011926426204e730d164a0d0f16", null);
        test.apply("5369dfb71b36ed2b91ca43f388b869e617558165e4f8306b80857d88bdd624f2", null);
    }

    @Test
    public void computeSamouraiFeesAddress() {
        Assert.assertEquals(tx0Service.computeSamouraiFeesAddress(1), "tb1qz84ma37y3d759sdy7mvq3u4vsxlg2qahw3lm23");
        Assert.assertEquals(tx0Service.computeSamouraiFeesAddress(2), "tb1qk20n7cpza4eakn0vqyfwskdgpwwwy29l5ek93w");
        Assert.assertEquals(tx0Service.computeSamouraiFeesAddress(3), "tb1qs394xtk0cx8ls9laymdn3scdrss3c2c20gttdx");
    }

    @Test
    public void isTx0FeesPaid() throws Exception {
        String txid = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";

        // accept when paid exact fee
        Assert.assertTrue(doIsTx0FeesPaid(txid, 975000, 1));

        // accept when paid more than fee
        Assert.assertTrue(doIsTx0FeesPaid(txid, 974999, 1));
        Assert.assertTrue(doIsTx0FeesPaid(txid, 1, 1));

        // reject when paid less than fee
        Assert.assertFalse(doIsTx0FeesPaid(txid,  975001, 1));
        Assert.assertFalse(doIsTx0FeesPaid(txid,  1000000, 1));

        // reject when paid to wrong xpub indice
        Assert.assertFalse(doIsTx0FeesPaid(txid, 975000, 0));
        Assert.assertFalse(doIsTx0FeesPaid(txid, 975000, 2));
        Assert.assertFalse(doIsTx0FeesPaid(txid, 975000, 10));
    }

    private boolean doIsTx0FeesPaid(String txid, int minFees, int xpubIndice) {
        RpcTransaction rpcTransaction = blockchainDataService.getRpcTransaction(txid);
        return tx0Service.isTx0FeesPaid(rpcTransaction, minFees, xpubIndice);
    }

    /*@Test
    public void checkWhirlpoolTx() throws Exception {
        String txid = "3bb546df988d8a577c2b2f216a18b7e337ebaf759187ae88e0eee01829f04eb1";

        // accept when valid tx
        doCheckWhirlpoolTx(txid, 975000, 10000000);
    }

    private void doCheckWhirlpoolTx(String utxoHash, long samouraiFeesMin, long denomination) throws IllegalInputException {
        RpcTransaction rpcTransaction = blockchainDataService.getRpcTransaction(utxoHash);
        Assert.assertNotNull(rpcTransaction);

        tx0Service.checkWhirlpoolTx(rpcTransaction, samouraiFeesMin, denomination, new ArrayList<>());
    }*/

    @Test
    public void checkInput() throws Exception {
        String txid = "cb2fad88ae75fdabb2bcc131b2f4f0ff2c82af22b6dd804dc341900195fb6187";

        // accept when valid mustMix, paid exact fee
        for (int i=0;i<8;i++) {
            Assert.assertFalse(doCheckInput(txid, i, 975000));
        }

        // accept when valid mustMix, paid more than fee
        for (int i=0;i<8;i++) {
            Assert.assertFalse(doCheckInput(txid, i, 974999));
        }

        // reject when paid less than fee
        for (int i=0;i<8;i++) {
            thrown.expect(IllegalInputException.class);
            thrown.expectMessage("Input doesn't belong to a Samourai pre-mix wallet (fees payment not found for utxo c35f7dae54b61a3d0570461dfcaf545fa5ce14bcfc52b1b7fd0c2a7d8d355aad-"+i+", x=1) (verified path:)");
            doCheckInput(txid, i, 975001);
        }
    }

    private boolean doCheckInput(String utxoHash, long utxoIndex, int minFees) throws IllegalInputException {
        RpcTransaction rpcTransaction = blockchainDataService.getRpcTransaction(utxoHash);
        Assert.assertNotNull(rpcTransaction);

        RpcOut rpcOut = Utils.findTxOutput(rpcTransaction, utxoIndex);
        Assert.assertNotNull(rpcOut);

        boolean isLiquidity = tx0Service.checkInput(rpcOut, rpcTransaction, minFees);
        return isLiquidity;
    }
}