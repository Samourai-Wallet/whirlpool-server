package com.samourai.whirlpool.server.integration;

import com.samourai.whirlpool.server.beans.Mix;
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
        // start mix
        String mixId = "foo";
        long denomination = 200000000;
        long fees = 100000;
        int minMustMix = NB_CLIENTS;
        int targetAnonymitySet = NB_CLIENTS;
        int minAnonymitySet = NB_CLIENTS;
        int maxAnonymitySet = NB_CLIENTS;
        long timeoutAdjustAnonymitySet = 10 * 60; // 10 minutes
        long timeoutAcceptLiquidities = 60;
        Mix mix = new Mix(mixId, denomination, fees, minMustMix,  targetAnonymitySet, minAnonymitySet, maxAnonymitySet, timeoutAdjustAnonymitySet, timeoutAcceptLiquidities);
        mixService.__reset(mix);

        MultiClientManager multiClientManager = multiClientManager(NB_CLIENTS, mix);

        // connect all clients except one, to stay in REGISTER_INPUTS
        log.info("# Connect first clients...");
        for (int i=0; i<NB_CLIENTS-1; i++) {
            final int clientIndice = i;
            taskExecutor.execute(() -> multiClientManager.connectWithMockOrFail(clientIndice, false, 1));
        }

        // connected clients should have registered their inputs...
        multiClientManager.assertMixStatusRegisterInput(NB_CLIENTS-1, false);

        // connect last client
        log.info("# Connect last client...");
        taskExecutor.execute(() -> multiClientManager.connectWithMockOrFail(NB_CLIENTS-1, false, 1));

        // all clients should have registered their inputs
        // mix automatically switches to REGISTER_OUTPUTS, then SIGNING

        // all clients should have registered their outputs and signed
        multiClientManager.assertMixStatusSuccess(NB_CLIENTS, false);
    }

}