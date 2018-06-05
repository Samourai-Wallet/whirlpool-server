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
public class Whirlpool10ClientsIntegrationTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Test
    public void whirlpool_10clients() throws Exception {
        final int NB_CLIENTS = 10;
        // start round
        String roundId = "foo";
        long denomination = 200000000;
        long fees = 100000;
        int targetMustMix = NB_CLIENTS;
        int minMustMix = NB_CLIENTS;
        long mustMixAdjustTimeout = 10 * 60; // 10 minutes
        float liquidityRatio = 0; // no liquidity for this test
        Round round = new Round(roundId, denomination, fees, targetMustMix, minMustMix, mustMixAdjustTimeout, liquidityRatio);
        roundService.__reset(round);

        MultiClientManager multiClientManager = multiClientManager(NB_CLIENTS, round);

        // connect all clients except one, to stay in REGISTER_INPUTS
        log.info("# Connect first clients...");
        for (int i=0; i<NB_CLIENTS-1; i++) {
            final int clientIndice = i;
            taskExecutor.execute(() -> multiClientManager.connectOrFail(clientIndice, false));
        }

        // connected clients should have registered their inputs...
        multiClientManager.assertRoundStatusRegisterInput(NB_CLIENTS-1, false);

        // connect last client
        log.info("# Connect last client...");
        taskExecutor.execute(() -> multiClientManager.connectOrFail(NB_CLIENTS-1, false));

        // all clients should have registered their inputs
        // round automatically switches to REGISTER_OUTPUTS, then SIGNING

        // all clients should have registered their outputs and signed
        multiClientManager.assertRoundStatusSuccess(NB_CLIENTS, false);
    }

}