package com.samourai.whirlpool.server.utils;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.simple.ISimpleWhirlpoolClient;
import com.samourai.whirlpool.client.simple.SimpleWhirlpoolClient;
import com.samourai.whirlpool.client.utils.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;
import com.samourai.whirlpool.server.beans.LiquidityPool;
import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.services.CryptoService;
import com.samourai.whirlpool.server.services.RoundLimitsManager;
import org.bitcoinj.core.ECKey;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class MultiClientManager {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private TestUtils testUtils;
    private CryptoService cryptoService;
    private RoundLimitsManager roundLimitsManager;
    private int port;

    private Round round;

    private TxOutPoint[] inputs;
    private ECKey[] inputKeys;
    private BIP47Wallet[] bip47Wallets;
    private WhirlpoolClient[] whirlpoolClients;

    public MultiClientManager(int nbClients, Round round, TestUtils testUtils, CryptoService cryptoService, RoundLimitsManager roundLimitsManager, int port) {
        this.round = round;
        this.testUtils = testUtils;
        this.cryptoService = cryptoService;
        this.roundLimitsManager = roundLimitsManager;
        this.port = port;

        inputs = new TxOutPoint[nbClients];
        inputKeys = new ECKey[nbClients];
        bip47Wallets = new BIP47Wallet[nbClients];
        whirlpoolClients = new WhirlpoolClient[nbClients];
    }

    private WhirlpoolClient createClient() {
        String wsUrl = "ws://127.0.0.1:" + port;
        WhirlpoolClientConfig config = new WhirlpoolClientConfig(wsUrl, cryptoService.getNetworkParameters());
        return new WhirlpoolClient(config);
    }

    private void prepareClient(int i, boolean liquidity) throws Exception {
        SegwitAddress inputAddress = testUtils.createSegwitAddress();
        BIP47Wallet bip47Wallet = testUtils.generateWallet(49).getBip47Wallet();

        prepareClient(i, liquidity, inputAddress, bip47Wallet, null, null, null);
    }

    private void prepareClient(int i, boolean liquidity, SegwitAddress inputAddress, BIP47Wallet bip47Wallet, Integer nbConfirmations, String utxoHash, Integer utxoIndex) throws Exception {
        whirlpoolClients[i] = createClient();
        // prepare input & output and mock input
        long amount = testUtils.computeSpendAmount(round, liquidity);
        inputs[i] = testUtils.createAndMockTxOutPoint(inputAddress, amount, nbConfirmations, utxoHash, utxoIndex);
        inputKeys[i] = inputAddress.getECKey();

        bip47Wallets[i] = bip47Wallet;
    }

    public void connectOrFail(int i, boolean liquidity) {
        try {
            connect(i, liquidity);
        }
        catch(Exception e) {
            log.error("", e);
            Assert.assertTrue(false);
        }
    }

    public void connect(int i, boolean liquidity) throws Exception {
        prepareClient(i, liquidity);
        whirlpool(i, liquidity);
    }

    public void connect(int i, boolean liquidity, SegwitAddress inputAddress, BIP47Wallet bip47Wallet, Integer nbConfirmations, String utxoHash, Integer utxoIndex) throws Exception {
        prepareClient(i, liquidity, inputAddress, bip47Wallet, nbConfirmations, utxoHash, utxoIndex);
        whirlpool(i, liquidity);
    }

    private void whirlpool(int i, boolean liquidity) throws Exception {

        WhirlpoolClient whirlpoolClient = whirlpoolClients[i];
        TxOutPoint utxo = inputs[i];
        ECKey ecKey = inputKeys[i];

        BIP47Wallet bip47Wallet = bip47Wallets[i];
        String paymentCode = bip47Wallet.getAccount(0).getPaymentCode();

        ISimpleWhirlpoolClient keySigner = new SimpleWhirlpoolClient(ecKey, bip47Wallet);
        whirlpoolClient.whirlpool(utxo.getHash(), utxo.getIndex(), paymentCode, keySigner, liquidity);
    }

    private void waitRegisteredInputs(int nbInputsExpected) throws Exception {
        int MAX_WAITS = 5;
        int WAIT_DURATION = 4000;
        for (int i=0; i<MAX_WAITS; i++) {
            String msg = "# ("+(i+1)+"/"+MAX_WAITS+") Waiting for registered inputs: " + round.getNbInputs() + " vs " + nbInputsExpected;
            if (round.getNbInputs() != nbInputsExpected) {
                log.info(msg + " : waiting longer...");
                Thread.sleep(WAIT_DURATION);
            }
            else {
                log.info(msg + " : success");
                return;
            }
        }

        log.info("# (LAST) Waiting for registered inputs: "+round.getNbInputs()+" vs "+nbInputsExpected);
        Assert.assertEquals(nbInputsExpected, round.getNbInputs());
    }

    public void waitLiquiditiesInPool(int nbLiquiditiesInPoolExpected) throws Exception {
        LiquidityPool liquidityPool = roundLimitsManager.getLiquidityPool(round);

        int MAX_WAITS = 5;
        int WAIT_DURATION = 4000;
        for (int i=0; i<MAX_WAITS; i++) {
            String msg = "# ("+(i+1)+"/"+MAX_WAITS+") Waiting for liquidities in pool: " + liquidityPool.getNbLiquidities() + " vs " + nbLiquiditiesInPoolExpected;
            if (liquidityPool.getNbLiquidities() != nbLiquiditiesInPoolExpected) {
                log.info(msg + " : waiting longer...");
                Thread.sleep(WAIT_DURATION);
            }
            else {
                log.info(msg + " : success");
                return;
            }
        }

        log.info("# (LAST) Waiting for liquidities in pool: " + liquidityPool.getNbLiquidities() + " vs " + nbLiquiditiesInPoolExpected);
        Assert.assertEquals(nbLiquiditiesInPoolExpected, liquidityPool.getNbLiquidities());
    }

    private void waitRoundStatus(RoundStatus roundStatusExpected) throws Exception {
        int MAX_WAITS = 5;
        int WAIT_DURATION = 4000;
        for (int i=0; i<MAX_WAITS; i++) {
            String msg = "# ("+(i+1)+"/"+MAX_WAITS+") Waiting for roundStatus: " + round.getRoundStatus() + " vs " + roundStatusExpected;
            if (!round.getRoundStatus().equals(roundStatusExpected)) {
                log.info(msg + " : waiting longer...");
                Thread.sleep(WAIT_DURATION);
            }
            else {
                log.info(msg + " : success");
                return;
            }
        }

        log.info("# (LAST) Waiting for roundStatus: " + round.getRoundStatus() + " vs " + roundStatusExpected);
        Assert.assertEquals(roundStatusExpected, round.getRoundStatus());
    }

    public void assertRoundStatusRegisterInput(int nbInputsExpected, boolean hasLiquidityExpected) throws Exception {
        // wait inputs to register
        waitRegisteredInputs(nbInputsExpected);

        LiquidityPool liquidityPool = roundLimitsManager.getLiquidityPool(round);
        System.out.println("=> roundStatus="+round.getRoundStatus()+", nbInputs="+round.getNbInputs());

        // all clients should have registered their outputs
        Assert.assertEquals(RoundStatus.REGISTER_INPUT, round.getRoundStatus());
        Assert.assertEquals(nbInputsExpected, round.getNbInputs());
        Assert.assertEquals(hasLiquidityExpected, liquidityPool.hasLiquidity());
    }

    public void assertRoundStatusSuccess(int nbAllRegisteredExpected, boolean hasLiquidityExpected) throws Exception {
        // wait inputs to register
        waitRegisteredInputs(nbAllRegisteredExpected);

        Thread.sleep(5000);

        // round automatically switches to REGISTER_OUTPUTS, then SIGNING, then SUCCESS
        waitRoundStatus(RoundStatus.SUCCESS);
        Assert.assertEquals(RoundStatus.SUCCESS, round.getRoundStatus());
        Assert.assertEquals(nbAllRegisteredExpected, round.getNbInputs());

        LiquidityPool liquidityPool = roundLimitsManager.getLiquidityPool(round);
        Assert.assertEquals(hasLiquidityExpected, liquidityPool.hasLiquidity());

        // all clients should have registered their outputs
        Assert.assertEquals(nbAllRegisteredExpected, round.getSendAddresses().size());
        Assert.assertEquals(nbAllRegisteredExpected, round.getReceiveAddresses().size());

        // all clients should have signed
        Assert.assertEquals(nbAllRegisteredExpected, round.getNbSignatures());

        // all clients should be SUCCESS
        assertClientsSuccess();
    }

    private void assertClientsSuccess() {
        // all clients shoud have success status
        for (int i=0; i<whirlpoolClients.length; i++) {
            WhirlpoolClient whirlpoolClient = whirlpoolClients[i];
            Assert.assertEquals(RoundStatus.SUCCESS, whirlpoolClient.__getRoundStatusNotification().status);
            Assert.assertTrue(whirlpoolClient.isDone());
        }
    }

    public Round getRound() {
        return round;
    }

    public void disconnect() {
        for (WhirlpoolClient client : whirlpoolClients) {
            client.disconnect();
        }
    }
}
