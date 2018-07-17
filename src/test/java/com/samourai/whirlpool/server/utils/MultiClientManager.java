package com.samourai.whirlpool.server.utils;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.client.RoundParams;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.WhirlpoolMultiRoundClient;
import com.samourai.whirlpool.client.WhirlpoolMultiRoundClientListener;
import com.samourai.whirlpool.client.beans.RoundResultSuccess;
import com.samourai.whirlpool.client.simple.ISimpleWhirlpoolClient;
import com.samourai.whirlpool.client.simple.SimpleWhirlpoolClient;
import com.samourai.whirlpool.client.utils.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;
import com.samourai.whirlpool.server.beans.LiquidityPool;
import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.services.BlockchainDataService;
import com.samourai.whirlpool.server.services.CryptoService;
import com.samourai.whirlpool.server.services.RoundLimitsService;
import com.samourai.whirlpool.server.services.RoundService;
import org.bitcoinj.core.ECKey;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class MultiClientManager {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private RoundService roundService;
    private TestUtils testUtils;
    private CryptoService cryptoService;
    private BlockchainDataService blockchainDataService;
    private RoundLimitsService roundLimitsService;
    private int port;

    private Round round;

    private TxOutPoint[] inputs;
    private ECKey[] inputKeys;
    private BIP47Wallet[] bip47Wallets;
    private WhirlpoolMultiRoundClient[] multiRoundClients;
    private WhirlpoolMultiRoundClientListener[] listeners;


    public MultiClientManager(int nbClients, Round round, RoundService roundService, TestUtils testUtils, CryptoService cryptoService, BlockchainDataService blockchainDataService, RoundLimitsService roundLimitsService, int port) {
        this.round = round;
        this.roundService = roundService;
        this.testUtils = testUtils;
        this.cryptoService = cryptoService;
        this.blockchainDataService = blockchainDataService;
        this.roundLimitsService = roundLimitsService;
        this.port = port;

        inputs = new TxOutPoint[nbClients];
        inputKeys = new ECKey[nbClients];
        bip47Wallets = new BIP47Wallet[nbClients];
        multiRoundClients = new WhirlpoolMultiRoundClient[nbClients];
        listeners = new WhirlpoolMultiRoundClientListener[nbClients];
    }

    private WhirlpoolMultiRoundClient createClient() {
        String wsUrl = "ws://127.0.0.1:" + port;
        WhirlpoolClientConfig config = new WhirlpoolClientConfig(wsUrl, cryptoService.getNetworkParameters());
        return new WhirlpoolMultiRoundClient(config);
    }

    private void prepareClientWithMock(int i, boolean liquidity) throws Exception {
        SegwitAddress inputAddress = testUtils.createSegwitAddress();
        BIP47Wallet bip47Wallet = testUtils.generateWallet(49).getBip47Wallet();
        prepareClientWithMock(i, liquidity, inputAddress, bip47Wallet, null, null, null);
    }

    private void prepareClientWithMock(int i, boolean liquidity, SegwitAddress inputAddress, BIP47Wallet bip47Wallet, Integer nbConfirmations, String utxoHash, Integer utxoIndex) throws Exception {
        // prepare input & output and mock input
        long amount = testUtils.computeSpendAmount(round, liquidity);
        TxOutPoint utxo = testUtils.createAndMockTxOutPoint(inputAddress, amount, nbConfirmations, utxoHash, utxoIndex);
        ECKey utxoKey = inputAddress.getECKey();

        prepareClient(i, utxo, utxoKey, bip47Wallet);
    }

    private void prepareClient(int i, TxOutPoint utxo, ECKey utxoKey, BIP47Wallet bip47Wallet) {
        multiRoundClients[i] = createClient();
        multiRoundClients[i].setLogPrefix("multiClient#"+i);
        bip47Wallets[i] = bip47Wallet;
        inputs[i] = utxo;
        inputKeys[i] = utxoKey;
    }

    public void connectWithMockOrFail(int i, boolean liquidity, int rounds) {
        try {
            connectWithMock(i, liquidity, rounds);
        }
        catch(Exception e) {
            log.error("", e);
            Assert.assertTrue(false);
        }
    }

    public void connectWithMock(int i, boolean liquidity, int rounds) throws Exception {
        prepareClientWithMock(i, liquidity);
        whirlpool(i, liquidity, rounds);
    }

    public void connectWithMock(int i, boolean liquidity, int rounds, SegwitAddress inputAddress, BIP47Wallet bip47Wallet, Integer nbConfirmations, String utxoHash, Integer utxoIndex) throws Exception {
        prepareClientWithMock(i, liquidity, inputAddress, bip47Wallet, nbConfirmations, utxoHash, utxoIndex);
        whirlpool(i, liquidity, rounds);
    }

    public void connect(int i, boolean liquidity, int rounds, TxOutPoint utxo, ECKey utxoKey, BIP47Wallet bip47Wallet) {
        prepareClient(i, utxo, utxoKey, bip47Wallet);
        whirlpool(i, liquidity, rounds);
    }

    private void whirlpool(int i, boolean liquidity, int rounds) {

        WhirlpoolMultiRoundClient multiRoundClient = multiRoundClients[i];
        TxOutPoint utxo = inputs[i];
        ECKey ecKey = inputKeys[i];

        BIP47Wallet bip47Wallet = bip47Wallets[i];
        String paymentCode = bip47Wallet.getAccount(0).getPaymentCode();

        ISimpleWhirlpoolClient keySigner = new SimpleWhirlpoolClient(ecKey, bip47Wallet);

        RoundParams roundParams = new RoundParams(utxo.getHash(), utxo.getIndex(), paymentCode, keySigner, liquidity);
        WhirlpoolMultiRoundClientListener listener = computeListener();
        multiRoundClient.whirlpool(roundParams, rounds, listener);
    }

    private WhirlpoolMultiRoundClientListener computeListener() {
        WhirlpoolMultiRoundClientListener listener = new WhirlpoolMultiRoundClientListener() {
            @Override
            public void success(int nbRounds) {
            }

            @Override
            public void fail(int currentRound, int nbRounds) {

            }

            @Override
            public void roundSuccess(int currentRound, int nbRounds, RoundResultSuccess roundResultSuccess) {
            }

            @Override
            public void progress(int currentRound, int nbRounds, RoundStatus roundStatus, int currentStep, int nbSteps) {

            }
        };
        return listener;
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

        // debug on failure
        log.info("# (LAST) Waiting for registered inputs: "+round.getNbInputs()+" vs "+nbInputsExpected);
        if (round.getNbInputs() != nbInputsExpected) {
            debugClients();
        }
        Assert.assertEquals(nbInputsExpected, round.getNbInputs());
    }

    public void waitLiquiditiesInPool(int nbLiquiditiesInPoolExpected) throws Exception {
        LiquidityPool liquidityPool = roundLimitsService.getLiquidityPool(round);

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

        // debug on failure
        log.info("# (LAST) Waiting for liquidities in pool: " + liquidityPool.getNbLiquidities() + " vs " + nbLiquiditiesInPoolExpected);
        if (liquidityPool.getNbLiquidities() != nbLiquiditiesInPoolExpected) {
            debugClients();
        }
        Assert.assertEquals(nbLiquiditiesInPoolExpected, liquidityPool.getNbLiquidities());
    }

    public void waitRoundStatus(RoundStatus roundStatusExpected) throws Exception {
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

    public void setRoundNext() {
        Round nextRound = roundService.__getCurrentRound();
        Assert.assertNotEquals(round, nextRound);
        setRound(nextRound);
        log.info("============= NEW ROUND DETECTED: " + nextRound.getRoundId() + " =============");
    }

    public void assertRoundStatusRegisterInput(int nbInputsExpected, boolean hasLiquidityExpected) throws Exception {
        // wait inputs to register
        waitRegisteredInputs(nbInputsExpected);

        LiquidityPool liquidityPool = roundLimitsService.getLiquidityPool(round);
        System.out.println("=> roundStatus="+round.getRoundStatus()+", nbInputs="+round.getNbInputs());

        // all clients should have registered their outputs
        Assert.assertEquals(RoundStatus.REGISTER_INPUT, round.getRoundStatus());
        Assert.assertEquals(nbInputsExpected, round.getNbInputs());
        Assert.assertEquals(hasLiquidityExpected, liquidityPool.hasLiquidity());
    }

    public void assertRoundStatusSuccess(int nbAllRegisteredExpected, boolean hasLiquidityExpected) throws Exception {
        assertRoundStatusSuccess(nbAllRegisteredExpected, hasLiquidityExpected, 1);
    }

    public void assertRoundStatusSuccess(int nbAllRegisteredExpected, boolean hasLiquidityExpected, int numRound) throws Exception {
        // wait inputs to register
        waitRegisteredInputs(nbAllRegisteredExpected);

        Thread.sleep(2000);

        // round automatically switches to REGISTER_OUTPUTS, then SIGNING, then SUCCESS
        waitRoundStatus(RoundStatus.SUCCESS);
        Assert.assertEquals(RoundStatus.SUCCESS, round.getRoundStatus());
        Assert.assertEquals(nbAllRegisteredExpected, round.getNbInputs());

        LiquidityPool liquidityPool = roundLimitsService.getLiquidityPool(round);
        Assert.assertEquals(hasLiquidityExpected, liquidityPool.hasLiquidity());

        // all clients should have registered their outputs
        Assert.assertEquals(nbAllRegisteredExpected, round.getSendAddresses().size());
        Assert.assertEquals(nbAllRegisteredExpected, round.getReceiveAddresses().size());

        // all clients should have signed
        Assert.assertEquals(nbAllRegisteredExpected, round.getNbSignatures());

        // all clients should be SUCCESS
        assertClientsSuccess(nbAllRegisteredExpected, numRound);
    }

    private void assertClientsSuccess(int nbAllRegisteredExpected, int numRound) {
        // clients shoud have success status
        int nbSuccess = 0;
        for (int i=0; i<multiRoundClients.length; i++) {
            WhirlpoolMultiRoundClient multiRoundClient = multiRoundClients[i];
            WhirlpoolClient whirlpoolClient = multiRoundClient.getClient(numRound);
            if (RoundStatus.SUCCESS.equals(whirlpoolClient.__getRoundStatusNotification().status) && whirlpoolClient.isDone()) {
                nbSuccess++;
            }
        }
        if (nbAllRegisteredExpected != nbSuccess) {
            debugClients();
        }
        Assert.assertEquals(nbAllRegisteredExpected, nbSuccess);
    }

    public void roundNextTargetAnonymitySetAdjustment() throws Exception {
        int targetAnonymitySetExpected = round.getTargetAnonymitySet() - 1;
        if (targetAnonymitySetExpected < round.getMinAnonymitySet()) {
            throw new Exception("targetAnonymitySetExpected < minAnonymitySet");
        }

        log.info("roundNextTargetAnonymitySetAdjustment: "+ (targetAnonymitySetExpected+1) + " -> " + targetAnonymitySetExpected);

        // simulate 9min58 elapsed... round targetAnonymitySet should remain unchanged
        roundLimitsService.__simulateElapsedTime(round, round.getTimeoutAdjustAnonymitySet()-2);
        Thread.sleep(1000);
        Assert.assertEquals(targetAnonymitySetExpected + 1, round.getTargetAnonymitySet());

        // a few seconds more, round targetAnonymitySet should be decreased
        waitRoundTargetAnonymitySet(targetAnonymitySetExpected);
    }

    public void waitRoundTargetAnonymitySet(int targetAnonymitySetExpected) throws Exception {
        int MAX_WAITS = 5;
        int WAIT_DURATION = 1000;
        for (int i=0; i<MAX_WAITS; i++) {
            String msg = "# ("+(i+1)+"/"+MAX_WAITS+") Waiting for roundTargetAnonymitySet: " + round.getTargetAnonymitySet() + " vs " + targetAnonymitySetExpected;
            if (round.getTargetAnonymitySet() != targetAnonymitySetExpected) {
                log.info(msg + " : waiting longer...");
                Thread.sleep(WAIT_DURATION);
            }
            else {
                log.info(msg + " : success");
                return;
            }
        }

        log.info("# (LAST) Waiting for roundTargetAnonymitySet: " + round.getTargetAnonymitySet() + " vs " + targetAnonymitySetExpected);
        Assert.assertEquals(targetAnonymitySetExpected, round.getTargetAnonymitySet());
    }

    public Round getRound() {
        return round;
    }

    public void setRound(Round round) {
        this.round = round;
    }

    public void exit() {
        for (WhirlpoolMultiRoundClient multiRoundClient : multiRoundClients) {
            if (multiRoundClient != null) {
                multiRoundClient.exit();
            }
        }
    }

    private void debugClients() {
        if (log.isDebugEnabled()) {
            log.debug("%%% debugging clients states... %%%");
            for (WhirlpoolMultiRoundClient multiRoundClient : multiRoundClients) {
                if (multiRoundClient != null) {
                    multiRoundClient.debugState();
                }
            }
        }
    }
}
