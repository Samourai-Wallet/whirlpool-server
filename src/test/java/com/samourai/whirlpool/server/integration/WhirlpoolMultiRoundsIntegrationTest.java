package com.samourai.whirlpool.server.integration;

import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.utils.MultiClientManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.invoke.MethodHandles;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = DEFINED_PORT)
public class WhirlpoolMultiRoundsIntegrationTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Test
    public void whirlpool_2clients_2rounds() throws Exception {
        final int NB_CLIENTS = 3;
        final int NB_CLIENTS_FIRST_ROUND = 2;

        // ROUND #1
        String roundId = "foo";
        long denomination = 200000000;
        long fees = 100000;
        int targetMustMix = NB_CLIENTS_FIRST_ROUND;
        int minMustMix = 1;
        long mustMixAdjustTimeout = 10 * 60; // 10 minutes
        float liquidityRatio = 0; // no liquidity for this test
        Round round = new Round(roundId, denomination, fees, targetMustMix, minMustMix, mustMixAdjustTimeout, liquidityRatio);
        roundService.__reset(round);

        MultiClientManager multiClientManager = multiClientManager(NB_CLIENTS, round);

        // connect 2 clients
        log.info("# Connect 2 clients for first round...");
        for (int i=0; i<NB_CLIENTS_FIRST_ROUND; i++) {
            final int clientIndice = i;
            taskExecutor.execute(() -> multiClientManager.connectOrFail(clientIndice, false, 2)); // stay for 2 rounds
        }

        // all clients should have registered their outputs and signed
        multiClientManager.assertRoundStatusSuccess(NB_CLIENTS_FIRST_ROUND, false);

        // ROUND #2
        Thread.sleep(2000);
        multiClientManager.setRoundNext();

        // the 2 clients from first round are liquidities for second round
        multiClientManager.waitLiquiditiesInPool(NB_CLIENTS_FIRST_ROUND);

        log.info("# Connect 1 mustMix for second round...");
        taskExecutor.execute(() -> multiClientManager.connectOrFail(2, false, 1));

        // we have 1 mustMix + 2 liquidities
        multiClientManager.assertRoundStatusRegisterInput(1, true);

        multiClientManager.roundNextTargetMustMixAdjustment();

        //multiClientManager.assertRoundStatusSuccess(2, true); // still one liquidity
    }

}