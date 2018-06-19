package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.v1.messages.PeersPaymentCodesResponse;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.utils.Utils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class RoundServiceTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    private RoundService roundService;

    @Autowired
    private DbService dbService;

    @Before
    public void setUp() throws Exception {
        dbService.__reset();
        roundService.__nextRound();
    }

    @Test
    public void isRegisterInputReady_noLiquidity() throws Exception {
        RoundService spyRoundService = Mockito.spy(roundService);
        int minMustMix = 1;
        int targetAnonymitySet = 2;
        int minAnonymitySet = 2;
        int maxAnonymitySet = 2;
        long timeoutAdjustAnonymitySet = 10 * 60;
        long timeoutAcceptLiquidities = 60; // TODO no liquidity
        Round round = new Round("foo", 0, 0, minMustMix,  targetAnonymitySet, minAnonymitySet, maxAnonymitySet, timeoutAdjustAnonymitySet, timeoutAcceptLiquidities);

        // 0 mustMix => false
        Assert.assertFalse(spyRoundService.isRegisterInputReady(round));

        // 1 mustMix => false
        round.registerInput(new RegisteredInput("mustMix1", generateInputsList(1).get(0), null, null, false));
        Assert.assertFalse(spyRoundService.isRegisterInputReady(round));

        // 2 mustMix => true
        round.registerInput(new RegisteredInput("mustMix2", generateInputsList(1).get(0), null, null, false));
        Assert.assertTrue(spyRoundService.isRegisterInputReady(round));
    }

    @Test
    public void isRegisterInputReady_withLiquidityBefore() throws Exception {
        RoundService spyRoundService = Mockito.spy(roundService);
        int minMustMix = 1;
        int targetAnonymitySet = 2;
        int minAnonymitySet = 2;
        int maxAnonymitySet = 2;
        long timeoutAdjustAnonymitySet = 10 * 60;
        long timeoutAcceptLiquidities = 60; // TODO with liquidity
        Round round = new Round("foo", 0, 0, minMustMix,  targetAnonymitySet, minAnonymitySet, maxAnonymitySet, timeoutAdjustAnonymitySet, timeoutAcceptLiquidities);

        // 0 liquidity => false
        Assert.assertFalse(spyRoundService.isRegisterInputReady(round));

        // 1 liquidity => false
        round.registerInput(new RegisteredInput("liquidity1", generateInputsList(1).get(0), null, null, true));
        Assert.assertFalse(spyRoundService.isRegisterInputReady(round));

        // 2 liquidity => false
        round.registerInput(new RegisteredInput("liquidity2", generateInputsList(1).get(0), null, null, true));
        Assert.assertFalse(spyRoundService.isRegisterInputReady(round));

        // 1 mustMix => false
        round.registerInput(new RegisteredInput("mustMix1", generateInputsList(1).get(0), null, null, false));
        Assert.assertFalse(spyRoundService.isRegisterInputReady(round));

        // 2 mustMix => true
        round.registerInput(new RegisteredInput("mustMix2", generateInputsList(1).get(0), null, null, false));
        Assert.assertTrue(spyRoundService.isRegisterInputReady(round));
    }

    @Test
    public void isRegisterInputReady_withLiquidityAfter() throws Exception {
        RoundService spyRoundService = Mockito.spy(roundService);
        int minMustMix = 1;
        int targetAnonymitySet = 2;
        int minAnonymitySet = 2;
        int maxAnonymitySet = 2;
        long timeoutAdjustAnonymitySet = 10 * 60;
        long timeoutAcceptLiquidities = 60; // TODO with liquidity
        Round round = new Round("foo", 0, 0, minMustMix,  targetAnonymitySet, minAnonymitySet, maxAnonymitySet, timeoutAdjustAnonymitySet, timeoutAcceptLiquidities);

        // 0 mustMix => false
        Assert.assertFalse(spyRoundService.isRegisterInputReady(round));

        // 1 mustMix => false
        round.registerInput(new RegisteredInput("mustMix1", generateInputsList(1).get(0), null, null, false));
        Assert.assertFalse(spyRoundService.isRegisterInputReady(round));

        // 2 mustMix => false
        round.registerInput(new RegisteredInput("mustMix2", generateInputsList(1).get(0), null, null, false));
        Assert.assertFalse(spyRoundService.isRegisterInputReady(round));

        // 1 liquidity => false
        round.registerInput(new RegisteredInput("liquidity1", generateInputsList(1).get(0), null, null, true));
        Assert.assertFalse(spyRoundService.isRegisterInputReady(round));

        // 2 liquidity => true
        round.registerInput(new RegisteredInput("liquidity2", generateInputsList(1).get(0), null, null, true));
        Assert.assertTrue(spyRoundService.isRegisterInputReady(round));
    }

    @Test
    public void computePaymentCodesConfrontations_shouldFail() throws Exception {
        // test should fail for 1 user
        runComputePaymentCodesConfrontations(1, false);
    }

    @Test
    public void computePaymentCodesConfrontations_shouldSucceed() throws Exception {
        // test should succeed starting from 2 users
        for (int nbUsers=2; nbUsers<=20; nbUsers++) {
            log.info("computePaymentCodesConfrontations : test with "+nbUsers+" users");
            runComputePaymentCodesConfrontations(nbUsers, true);
        }
    }

    private void runComputePaymentCodesConfrontations(int nbUsers, boolean shouldSucceed) throws Exception {
        Map<String, String> paymentCodesByUser = new HashMap<>();
        for (int i=1; i<=nbUsers; i++) {
            paymentCodesByUser.put("user"+i, "paymentCode"+i);
        }
        Map<String,PeersPaymentCodesResponse> paymentCodeConfrontations = roundService.computePaymentCodesConfrontations(paymentCodesByUser);
        verifyPaymentCodeConfrontations(paymentCodeConfrontations, paymentCodesByUser, shouldSucceed);
    }

    private void verifyPaymentCodeConfrontations(Map<String,PeersPaymentCodesResponse> paymentCodeConfrontations, Map<String,String> paymentCodesByUser, boolean shouldSucceed) {
        Map<String,Integer> paymentCodesPairsCount = new HashMap<>();
        for (Map.Entry<String,PeersPaymentCodesResponse> paymentCodeConfrontationEntry : paymentCodeConfrontations.entrySet()) {
            String username = paymentCodeConfrontationEntry.getKey();
            String userPaymentCode = paymentCodesByUser.get(username);

            // verify that user never confrontates with his own paymentCode
            PeersPaymentCodesResponse response = paymentCodeConfrontationEntry.getValue();
            Assert.assertEquals (!response.fromPaymentCode.equals(userPaymentCode), shouldSucceed);
            Assert.assertEquals (!response.toPaymentCode.equals(userPaymentCode), shouldSucceed);

            String sendingPair = userPaymentCode+":"+response.toPaymentCode;
            paymentCodesPairsCount.put(sendingPair, paymentCodesPairsCount.getOrDefault(sendingPair, 0)+1);
            log.info("sendingPair: "+sendingPair);

            String receivingPair = response.fromPaymentCode+":"+userPaymentCode;
            log.info("receivingPair: "+receivingPair);
            paymentCodesPairsCount.put(receivingPair, paymentCodesPairsCount.getOrDefault(receivingPair, 0)+1);
        }

        log.info("pairs: "+paymentCodesPairsCount);

        // pairs should appear twice (one for sending user, one for receiving user)
        paymentCodesPairsCount.values().forEach(value -> Assert.assertEquals(new Integer(2), value));
    }

    private List<TxOutPoint> generateInputsList(int nb) {
        List<TxOutPoint> inputs = new ArrayList<>();
        while(inputs.size() < nb) {
            TxOutPoint txOutPoint = new TxOutPoint(Utils.getRandomString(65), 0, 99999);
            inputs.add(txOutPoint);
        }
        return inputs;
    }
}