package com.samourai.whirlpool.server.integration;

import com.google.common.primitives.Longs;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.simple.ISimpleWhirlpoolClient;
import com.samourai.whirlpool.client.simple.SimpleWhirlpoolClient;
import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;
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
import java.util.function.IntFunction;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class Whirlpool10ClientsIntegrationTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Test
    public void whirlpool_10clients() throws Exception {
        final int NB_CLIENTS = 10;
        whirlpoolClients = createClients(NB_CLIENTS);

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


        // prepare inputs & outputs
        TxOutPoint[] inputs = new TxOutPoint[NB_CLIENTS];
        ECKey[] inputKeys = new ECKey[NB_CLIENTS];
        SegwitAddress[] outputs = new SegwitAddress[NB_CLIENTS];
        final long amount = computeSpendAmount(round, false);
        for (int i=0; i<whirlpoolClients.length; i++) {
            SegwitAddress inputAddress = testUtils.createSegwitAddress();
            inputs[i] = createAndMockTxOutPoint(inputAddress, amount);
            inputKeys[i] = inputAddress.getECKey();

            SegwitAddress outputAddress = testUtils.createSegwitAddress();
            outputs[i] = outputAddress;
        }


        final IntFunction connectClient = (int i) -> {
            WhirlpoolClient whirlpoolClient = whirlpoolClients[i];
            TxOutPoint utxo = inputs[i];
            ECKey ecKey = inputKeys[i];
            try {
                BIP47Wallet bip47Wallet = testUtils.generateWallet(49).getBip47Wallet();
                String paymentCode = bip47Wallet.getAccount(0).getPaymentCode();
                ISimpleWhirlpoolClient keySigner = new SimpleWhirlpoolClient(ecKey, bip47Wallet);
                whirlpoolClient.whirlpool(utxo.getHash(), utxo.getIndex(), paymentCode, keySigner, false);
            } catch (Exception e) {
                log.error("", e);
                Assert.assertTrue(false);
            }
            return null;
        };

        // connect all clients except one, to stay in REGISTER_INPUTS
        log.info("# Connect first clients...");
        for (int i=0; i<whirlpoolClients.length-1; i++) {
            final int clientIndice = i;
            taskExecutor.execute(() -> connectClient.apply(clientIndice));
        }
        Thread.sleep(5000);

        // connected clients should have registered their inputs...
        Assert.assertEquals(RoundStatus.REGISTER_INPUT, round.getRoundStatus());
        Assert.assertEquals(NB_CLIENTS-1, round.getInputs().size());

        // connect last client
        Thread.sleep(500);
        log.info("# Connect last client...");
        taskExecutor.execute(() -> connectClient.apply(whirlpoolClients.length-1));
        Thread.sleep(7000);

        // all clients should have registered their inputs
        //assertStatusRegisterInput(round, NB_CLIENTS, false);

        // round automatically switches to REGISTER_OUTPUTS, then SIGNING
        Thread.sleep(4000);

        // all clients should have registered their outputs and signed
        assertStatusSuccess(round, NB_CLIENTS, false);
        assertClientsSuccess();
    }

}