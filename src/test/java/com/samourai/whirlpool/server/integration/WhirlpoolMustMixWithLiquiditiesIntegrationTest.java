package com.samourai.whirlpool.server.integration;

import com.samourai.whirlpool.server.beans.LiquidityPool;
import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.utils.MultiClientManager;
import org.junit.Assert;
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
public class WhirlpoolMustMixWithLiquiditiesIntegrationTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private MultiClientManager runMustmixWithliquidities(int targetMustMix, int minMustMix, int NB_MUSTMIX_CONNECTING, int NB_LIQUIDITIES_CONNECTING) throws Exception {
        final int NB_ALL_CONNECTING = NB_MUSTMIX_CONNECTING + NB_LIQUIDITIES_CONNECTING;

        // start round
        String roundId = "foo";
        long denomination = 200000000;
        long fees = 100000;
        long mustMixAdjustTimeout = 10 * 60; // 10 minutes
        float liquidityRatio = 1; // 1 liquidity for 1 mustMix
        Round round = new Round(roundId, denomination, fees, targetMustMix, minMustMix, mustMixAdjustTimeout, liquidityRatio);
        roundService.__reset(round);

        MultiClientManager multiClientManager = multiClientManager(NB_ALL_CONNECTING, round);

        LiquidityPool liquidityPool = roundLimitsManager.getLiquidityPool(round);
        Assert.assertFalse(liquidityPool.hasLiquidity());

        // connect liquidities first
        log.info("# Begin connecting "+NB_LIQUIDITIES_CONNECTING+" liquidities...");
        for (int i=0; i<NB_LIQUIDITIES_CONNECTING; i++) {
            log.info("Connecting liquidity #"+(i+1)+"/"+NB_LIQUIDITIES_CONNECTING);
            final int clientIndice = i;
            taskExecutor.execute(() -> multiClientManager.connectOrFail(clientIndice, true));
        }

        // liquidities should be placed on queue...
        multiClientManager.waitLiquiditiesInPool(NB_LIQUIDITIES_CONNECTING);
        multiClientManager.assertRoundStatusRegisterInput(0, true);

        // connect clients wanting to mix
        log.info("# Begin connecting "+NB_MUSTMIX_CONNECTING+" mustMix...");
        for (int i=NB_LIQUIDITIES_CONNECTING; i<NB_ALL_CONNECTING; i++) {
            log.info("Connecting mustMix #"+(i+1)+"/"+NB_ALL_CONNECTING);
            final int clientIndice = i;
            taskExecutor.execute(() -> multiClientManager.connectOrFail(clientIndice, false));
        }

        return multiClientManager;
    }

    @Test
    public void whirlpool_5mustMix5liquidities() throws Exception {
        // mustMix + liquidities will immediately be registered, targetMustMix won't be decreased
        final int NB_MUSTMIX_CONNECTING = 5;
        final int NB_LIQUIDITIES_CONNECTING = 5;
        int targetMustMix = 5;
        int minMustMix = 5;

        int NB_MUSTMIX_EXPECTED = 5;
        final int NB_ALL_REGISTERED_EXPECTED = NB_MUSTMIX_EXPECTED * 2; // 1 liquidity per mustMix

        // TEST
        MultiClientManager multiClientManager = runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY
        multiClientManager.assertRoundStatusSuccess(NB_ALL_REGISTERED_EXPECTED, false);
    }

    @Test
    public void whirlpool_7mustMix7liquidities() throws Exception {
        // mustMix + liquidities will immediately be registered, targetMustMix won't be decreased
        final int NB_MUSTMIX_CONNECTING = 7;
        final int NB_LIQUIDITIES_CONNECTING = 7;
        int targetMustMix = 7;
        int minMustMix = 5;

        int NB_MUSTMIX_EXPECTED = 7;
        final int NB_ALL_REGISTERED_EXPECTED = NB_MUSTMIX_EXPECTED * 2; // 1 liquidity per mustMix

        // TEST
        MultiClientManager multiClientManager = runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY
        multiClientManager.assertRoundStatusSuccess(NB_ALL_REGISTERED_EXPECTED, false);
    }

    @Test
    public void whirlpool_fail_notEnoughMustMix() throws Exception {
        // will miss 1 mustMix
        final int NB_MUSTMIX_CONNECTING = 4;
        final int NB_LIQUIDITIES_CONNECTING = 5;
        int targetMustMix = 5;
        int minMustMix = 5;

        final int NB_ALL_REGISTERED_EXPECTED = 4;

        // TEST
        MultiClientManager multiClientManager = runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY
        multiClientManager.assertRoundStatusRegisterInput(NB_ALL_REGISTERED_EXPECTED, true); // liquidities not registered yet
    }

    @Test
    public void whirlpool_fail_notEnoughMustMix_2adjustments() throws Exception {
        // will miss 1 mustMix
        final int NB_MUSTMIX_CONNECTING = 4;
        final int NB_LIQUIDITIES_CONNECTING = 5;
        int targetMustMix = 7;
        int minMustMix = 5;

        int NB_MUSTMIX_EXPECTED = 4;
        final int NB_ALL_REGISTERED_EXPECTED = NB_MUSTMIX_EXPECTED * 2;

        // TEST
        MultiClientManager multiClientManager = runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY
        multiClientManager.assertRoundStatusRegisterInput(NB_MUSTMIX_EXPECTED, true);

        // ...targetMustMix lowered
        multiClientManager.roundNextTargetMustMixAdjustment();

        // unchanged
        multiClientManager.assertRoundStatusRegisterInput(NB_MUSTMIX_EXPECTED, true);

        // ...targetMustMix lowered
        multiClientManager.roundNextTargetMustMixAdjustment();

        // liquidities should have been registered
        multiClientManager.assertRoundStatusSuccess(NB_ALL_REGISTERED_EXPECTED, false);
    }

    @Test
    public void whirlpool_fail_notEnoughLiquidities() throws Exception {
        // mustMix + liquidities will be registered immediately but will miss 1 liquidity
        final int NB_MUSTMIX_CONNECTING = 5;
        final int NB_LIQUIDITIES_CONNECTING = 4;
        int targetMustMix = 5;
        int minMustMix = 5;

        final int NB_ALL_REGISTERED_EXPECTED = 9;

        // TEST
        MultiClientManager multiClientManager = runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY
        multiClientManager.assertRoundStatusRegisterInput(NB_ALL_REGISTERED_EXPECTED, false); // no more liquidity
    }

    @Test
    public void whirlpool_fail_notEnoughLiquidities_2adjustments() throws Exception {
        // mustMix + liquidities will be registered after 2 adjustments, but will miss 1 liquidity
        final int NB_MUSTMIX_CONNECTING = 5;
        final int NB_LIQUIDITIES_CONNECTING = 4;
        int targetMustMix = 7;
        int minMustMix = 5;

        int NB_MUSTMIX_EXPECTED = 5;
        final int NB_ALL_REGISTERED_EXPECTED = 9;

        // TEST
        MultiClientManager multiClientManager = runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY

        // clients should be registered, not liquidities yet
        multiClientManager.assertRoundStatusRegisterInput(NB_MUSTMIX_EXPECTED, true);

        // ...targetMustMix lowered
        multiClientManager.roundNextTargetMustMixAdjustment();

        // unchanged
        multiClientManager.assertRoundStatusRegisterInput(NB_MUSTMIX_EXPECTED, true);

        // ...targetMustMix lowered
        multiClientManager.roundNextTargetMustMixAdjustment();

        // mustMix + liquidities should have been registered
        multiClientManager.assertRoundStatusRegisterInput(NB_ALL_REGISTERED_EXPECTED, false); // no more liquidity
    }

    @Test
    public void whirlpool_success_moreLiquiditiesThanMustmix() throws Exception {
        // mustMix + some of liquidities will be registered immediately
        final int NB_MUSTMIX_CONNECTING = 5;
        final int NB_LIQUIDITIES_CONNECTING = 10;
        int targetMustMix = 5; //
        int minMustMix = 5;

        int NB_MUSTMIX_EXPECTED = 5;
        final int NB_ALL_REGISTERED_EXPECTED = NB_MUSTMIX_EXPECTED * 2; // 1 liquidity per mustMix

        // TEST
        MultiClientManager multiClientManager = runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY
        // mustMix + liquidities should have been registered
        multiClientManager.assertRoundStatusSuccess(NB_ALL_REGISTERED_EXPECTED, true); // still liquidity
    }

    @Test
    public void whirlpool_success_moreLiquiditiesThanMustmix_2Adjustements() throws Exception {
        // mustMix will be registered immediately, liquidities will be registered after 2 adjustments
        final int NB_MUSTMIX_CONNECTING = 5;
        final int NB_LIQUIDITIES_CONNECTING = 10;
        int targetMustMix = 7; //
        int minMustMix = 5;

        int NB_MUSTMIX_EXPECTED = 5;
        final int NB_ALL_REGISTERED_EXPECTED = NB_MUSTMIX_EXPECTED * 2; // 1 liquidity per mustMix

        // TEST
        MultiClientManager multiClientManager = runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY

        // clients should be registered, not liquidities yet
        multiClientManager.assertRoundStatusRegisterInput(NB_MUSTMIX_EXPECTED, true);

        // ...targetMustMix lowered
        multiClientManager.roundNextTargetMustMixAdjustment();

        // unchanged
        multiClientManager.assertRoundStatusRegisterInput(NB_MUSTMIX_EXPECTED, true);

        // ...targetMustMix lowered
        multiClientManager.roundNextTargetMustMixAdjustment();

        // mustMix + liquidities should have been registered
        multiClientManager.assertRoundStatusSuccess(NB_MUSTMIX_EXPECTED, true); // still liquidity
    }

}