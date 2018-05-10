package com.samourai.whirlpool.server.integration;

import com.google.common.primitives.Longs;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.simple.ISimpleWhirlpoolClient;
import com.samourai.whirlpool.client.simple.SimpleWhirlpoolClient;
import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;
import com.samourai.whirlpool.server.beans.LiquidityPool;
import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import org.bitcoinj.core.ECKey;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.invoke.MethodHandles;
import java.util.function.BiFunction;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class WhirlpoolMustMixWithLiquiditiesIntegrationTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private void runMustmixWithliquidities(int targetMustMix, int minMustMix, int NB_MUSTMIX_CONNECTING, int NB_LIQUIDITIES_CONNECTING) throws Exception {
        final int NB_ALL_CONNECTING = NB_MUSTMIX_CONNECTING + NB_LIQUIDITIES_CONNECTING;
        whirlpoolClients = createClients(NB_ALL_CONNECTING);

        // start round
        String roundId = "foo";
        long denomination = 200000000;
        long fees = 100000;
        long mustMixAdjustTimeout = 10 * 60; // 10 minutes
        float liquidityRatio = 1; // 1 liquidity for 1 mustMix
        Round round = new Round(roundId, denomination, fees, targetMustMix, minMustMix, mustMixAdjustTimeout, liquidityRatio);
        roundService.__reset(round);


        // prepare inputs & outputs
        TxOutPoint[] inputs = new TxOutPoint[NB_ALL_CONNECTING];
        ECKey[] inputKeys = new ECKey[NB_ALL_CONNECTING];
        SegwitAddress[] outputs = new SegwitAddress[NB_ALL_CONNECTING];
        for (int i=0; i<whirlpoolClients.length; i++) {
            SegwitAddress inputAddress = testUtils.createSegwitAddress();
            boolean liquidity = (i < NB_LIQUIDITIES_CONNECTING);
            inputs[i] = createAndMockTxOutPoint(inputAddress, computeSpendAmount(round, liquidity));
            inputKeys[i] = inputAddress.getECKey();

            SegwitAddress outputAddress = testUtils.createSegwitAddress();
            outputs[i] = outputAddress;
        }

        final BiFunction<Integer,Boolean,Boolean> connectClient = (Integer i, Boolean liquidity) -> {
            WhirlpoolClient whirlpoolClient = whirlpoolClients[i];
            TxOutPoint utxo = inputs[i];
            ECKey ecKey = inputKeys[i];
            try {
                BIP47Wallet bip47Wallet = testUtils.generateWallet(49).getBip47Wallet();
                String paymentCode = bip47Wallet.getAccount(0).getPaymentCode();
                ISimpleWhirlpoolClient keySigner = new SimpleWhirlpoolClient(ecKey, bip47Wallet);
                whirlpoolClient.whirlpool(utxo.getHash(), utxo.getIndex(), paymentCode, keySigner, computeSpendAmount(round, liquidity), liquidity);
            } catch (Exception e) {
                log.error("", e);
                Assert.assertTrue(false);
            }
            return null;
        };

        LiquidityPool liquidityPool = roundLimitsManager.getLiquidityPool(round);
        Assert.assertFalse(liquidityPool.hasLiquidity());

        // connect liquidities first
        log.info("# connect liquidities first...");
        for (int i=0; i<NB_LIQUIDITIES_CONNECTING; i++) {
            log.info("Connecting liquidity #"+i+"/"+NB_LIQUIDITIES_CONNECTING);
            final int clientIndice = i;
            taskExecutor.execute(() -> connectClient.apply(clientIndice, true));
        }
        Thread.sleep(5000);

        // liquidities should be placed on queue...
        roundService.changeRoundStatus(round.getRoundId(), RoundStatus.REGISTER_INPUT);
        Thread.sleep(1500);
        Assert.assertEquals(RoundStatus.REGISTER_INPUT, round.getRoundStatus());
        Assert.assertEquals(0, round.getNbInputs());
        Assert.assertTrue(liquidityPool.hasLiquidity());

        // connect clients wanting to mix
        log.info("# Connect clients wanting to mix...");
        for (int i=NB_LIQUIDITIES_CONNECTING; i<NB_ALL_CONNECTING; i++) {
            log.info("Connecting mustMix #"+i+"/"+NB_ALL_CONNECTING);
            final int clientIndice = i;
            taskExecutor.execute(() -> connectClient.apply(clientIndice, false));
        }
        Thread.sleep(15000);
    }

    @Test // TODO BUG
    public void whirlpool_5mustMix5liquidities() throws Exception {
        // mustMix + liquidities will immediately be registered, targetMustMix won't be decreased
        final int NB_MUSTMIX_CONNECTING = 5;
        final int NB_LIQUIDITIES_CONNECTING = 5;
        int targetMustMix = 5;
        int minMustMix = 5;

        int NB_MUSTMIX_EXPECTED = 5;
        final int NB_ALL_REGISTERED_EXPECTED = NB_MUSTMIX_EXPECTED * 2; // 1 liquidity per mustMix

        // TEST
        runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY
        Round round = roundService.__getCurrentRound();

        // liquidities should have been registered
        assertStatusSuccess(round, NB_ALL_REGISTERED_EXPECTED, false); // no more liquidity
        assertClientsSuccess();
    }

    @Test // TODO BUG
    public void whirlpool_7mustMix7liquidities() throws Exception {
        // mustMix + liquidities will immediately be registered, targetMustMix won't be decreased
        final int NB_MUSTMIX_CONNECTING = 7;
        final int NB_LIQUIDITIES_CONNECTING = 7;
        int targetMustMix = 7;
        int minMustMix = 5;

        int NB_MUSTMIX_EXPECTED = 7;
        final int NB_ALL_REGISTERED_EXPECTED = NB_MUSTMIX_EXPECTED * 2; // 1 liquidity per mustMix

        // TEST
        runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY
        Round round = roundService.__getCurrentRound();

        // liquidities should have been registered
        assertStatusSuccess(round, NB_ALL_REGISTERED_EXPECTED, false); // no more liquidity
        assertClientsSuccess();
    }

    @Test // TODO BUG
    public void whirlpool_fail_notEnoughMustMix() throws Exception {
        // will miss 1 mustMix
        final int NB_MUSTMIX_CONNECTING = 4;
        final int NB_LIQUIDITIES_CONNECTING = 5;
        int targetMustMix = 5;
        int minMustMix = 5;

        final int NB_ALL_REGISTERED_EXPECTED = 4;

        // TEST
        runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY
        Round round = roundService.__getCurrentRound();

        // liquidities should have been registered
        assertStatusRegisterInput(round, NB_ALL_REGISTERED_EXPECTED, true); // liquidities not registered yet
    }

    @Test
    public void whirlpool_fail_notEnoughMustMix_2adjustments() throws Exception {
        // will miss 1 mustMix
        final int NB_MUSTMIX_CONNECTING = 4;
        final int NB_LIQUIDITIES_CONNECTING = 5;
        int targetMustMix = 7;
        int minMustMix = 5;

        int NB_MUSTMIX_EXPECTED = 4;
        final int NB_ALL_REGISTERED_EXPECTED = 4;

        // TEST
        runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY
        Round round = roundService.__getCurrentRound();

        // clients should be registered, not liquidities yet
        assertStatusRegisterInput(round, NB_MUSTMIX_EXPECTED, true);

        // ...targetMustMix lowered
        simulateRoundNextTargetMustMixAdjustment(round, targetMustMix - 1);

        // unchanged
        assertStatusRegisterInput(round, NB_MUSTMIX_EXPECTED, true);

        // ...targetMustMix lowered
        simulateRoundNextTargetMustMixAdjustment(round, targetMustMix - 2);

        // liquidities should have been registered
        assertStatusRegisterInput(round, NB_ALL_REGISTERED_EXPECTED, true); // liquidities not registered yet
    }

    @Test // TODO BUG
    public void whirlpool_fail_notEnoughLiquidities() throws Exception {
        // mustMix + liquidities will be registered immediately but will miss 1 liquidity
        final int NB_MUSTMIX_CONNECTING = 5;
        final int NB_LIQUIDITIES_CONNECTING = 4;
        int targetMustMix = 5;
        int minMustMix = 5;

        final int NB_ALL_REGISTERED_EXPECTED = 9;

        // TEST
        runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY
        Round round = roundService.__getCurrentRound();

        // liquidities should have been registered
        assertStatusRegisterInput(round, NB_ALL_REGISTERED_EXPECTED, false); // no more liquidity
    }

    @Test // TODO BUG
    public void whirlpool_fail_notEnoughLiquidities_2adjustments() throws Exception {
        // mustMix + liquidities will be registered after 2 adjustments, but will miss 1 liquidity
        final int NB_MUSTMIX_CONNECTING = 5;
        final int NB_LIQUIDITIES_CONNECTING = 4;
        int targetMustMix = 7;
        int minMustMix = 5;

        int NB_MUSTMIX_EXPECTED = 5;
        final int NB_ALL_REGISTERED_EXPECTED = 9;

        // TEST
        runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY
        Round round = roundService.__getCurrentRound();

        // clients should be registered, not liquidities yet
        assertStatusRegisterInput(round, NB_MUSTMIX_EXPECTED, true);

        // ...targetMustMix lowered
        simulateRoundNextTargetMustMixAdjustment(round, targetMustMix - 1);

        // unchanged
        assertStatusRegisterInput(round, NB_MUSTMIX_EXPECTED, true);

        // ...targetMustMix lowered
        simulateRoundNextTargetMustMixAdjustment(round, targetMustMix - 2);

        // mustMix + liquidities should have been registered
        assertStatusRegisterInput(round, NB_ALL_REGISTERED_EXPECTED, false); // no more liquidity
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
        runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY
        Round round = roundService.__getCurrentRound();

        // mustMix + liquidities should have been registered
        assertStatusSuccess(round, NB_ALL_REGISTERED_EXPECTED, true); // still liquidity
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
        runMustmixWithliquidities(targetMustMix, minMustMix, NB_MUSTMIX_CONNECTING, NB_LIQUIDITIES_CONNECTING);

        // VERIFY
        Round round = roundService.__getCurrentRound();

        // clients should be registered, not liquidities yet
        assertStatusRegisterInput(round, NB_MUSTMIX_EXPECTED, true);

        // ...targetMustMix lowered
        simulateRoundNextTargetMustMixAdjustment(round, targetMustMix - 1);

        // unchanged
        assertStatusRegisterInput(round, NB_MUSTMIX_EXPECTED, true);

        // ...targetMustMix lowered
        simulateRoundNextTargetMustMixAdjustment(round, targetMustMix - 2);

        // mustMix + liquidities should have been registered
        assertStatusSuccess(round, NB_ALL_REGISTERED_EXPECTED, true); // still liquidity
        assertClientsSuccess();
    }

}