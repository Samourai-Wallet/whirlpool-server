package com.samourai.whirlpool.server.utils;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.WhirlpoolClientConfig;
import com.samourai.whirlpool.client.WhirlpoolClientImpl;
import com.samourai.whirlpool.client.WhirlpoolClientListener;
import com.samourai.whirlpool.client.beans.MixSuccess;
import com.samourai.whirlpool.client.mix.MixClient;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.IMixHandler;
import com.samourai.whirlpool.client.mix.handler.MixHandler;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.LiquidityPool;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.services.CryptoService;
import com.samourai.whirlpool.server.services.MixLimitsService;
import com.samourai.whirlpool.server.services.MixService;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class MultiClientManager {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private MixService mixService;
    private TestUtils testUtils;
    private CryptoService cryptoService;
    private MixLimitsService mixLimitsService;
    private int port;

    private Mix mix;

    private TxOutPoint[] inputs;
    private ECKey[] inputKeys;
    private BIP47Wallet[] bip47Wallets;
    private WhirlpoolClient[] clients;
    private WhirlpoolClientListener[] listeners;


    public MultiClientManager(int nbClients, Mix mix, MixService mixService, TestUtils testUtils, CryptoService cryptoService, MixLimitsService mixLimitsService, int port) {
        this.mix = mix;
        this.mixService = mixService;
        this.testUtils = testUtils;
        this.cryptoService = cryptoService;
        this.mixLimitsService = mixLimitsService;
        this.port = port;

        inputs = new TxOutPoint[nbClients];
        inputKeys = new ECKey[nbClients];
        bip47Wallets = new BIP47Wallet[nbClients];
        clients = new WhirlpoolClient[nbClients];
        listeners = new WhirlpoolClientListener[nbClients];
    }

    private WhirlpoolClient createClient() {
        String server = "127.0.0.1:" + port;
        WhirlpoolClientConfig config = new WhirlpoolClientConfig(server, cryptoService.getNetworkParameters());
        return WhirlpoolClientImpl.newClient(config);
    }

    private void prepareClientWithMock(int i, long inputBalance) throws Exception {
        SegwitAddress inputAddress = testUtils.createSegwitAddress();
        BIP47Wallet bip47Wallet = testUtils.generateWallet(49).getBip47Wallet();
        prepareClientWithMock(i, inputAddress, bip47Wallet, null, null, null, inputBalance);
    }

    private void prepareClientWithMock(int i, SegwitAddress inputAddress, BIP47Wallet bip47Wallet, Integer nbConfirmations, String utxoHash, Integer utxoIndex, long inputBalance) throws Exception {
        // prepare input & output and mock input
        TxOutPoint utxo = testUtils.createAndMockTxOutPoint(inputAddress, inputBalance, nbConfirmations, utxoHash, utxoIndex);
        ECKey utxoKey = inputAddress.getECKey();

        prepareClient(i, utxo, utxoKey, bip47Wallet);
    }

    private void prepareClient(int i, TxOutPoint utxo, ECKey utxoKey, BIP47Wallet bip47Wallet) {
        clients[i] = createClient();
        ((WhirlpoolClientImpl)clients[i]).setLogPrefix("multiClient#"+i);
        bip47Wallets[i] = bip47Wallet;
        inputs[i] = utxo;
        inputKeys[i] = utxoKey;
    }

    private long computeInputBalanceMin(boolean liquidity) {
        long inputBalance = WhirlpoolProtocol.computeInputBalanceMin(mix.getPool().getDenomination(), liquidity, mix.getPool().getMinerFeeMin());
        return inputBalance;
    }

    public void connectWithMockOrFail(int i, boolean liquidity, int mixs) {
        long inputBalance = computeInputBalanceMin(liquidity);
        connectWithMockOrFail(i, mixs, inputBalance);
    }

    public void connectWithMockOrFail(int i, int mixs, long inputBalance) {
        try {
            connectWithMock(i, mixs, inputBalance);
        }
        catch(Exception e) {
            log.error("", e);
            Assert.assertTrue(false);
        }
    }

    public void connectWithMock(int i, int mixs, long inputBalance) throws Exception {
        prepareClientWithMock(i, inputBalance);
        whirlpool(i, mixs);
    }

    public void connectWithMock(int i, int mixs, SegwitAddress inputAddress, BIP47Wallet bip47Wallet, Integer nbConfirmations, String utxoHash, Integer utxoIndex, long inputBalance) throws Exception {
        prepareClientWithMock(i, inputAddress, bip47Wallet, nbConfirmations, utxoHash, utxoIndex, inputBalance);
        whirlpool(i, mixs);
    }

    public void connect(int i, int mixs, TxOutPoint utxo, ECKey utxoKey, BIP47Wallet bip47Wallet) {
        prepareClient(i, utxo, utxoKey, bip47Wallet);
        whirlpool(i, mixs);
    }

    private void whirlpool(int i, int mixs) {
        String poolId = mix.getPool().getPoolId();
        WhirlpoolClient whirlpoolClient = clients[i];
        TxOutPoint utxo = inputs[i];
        ECKey ecKey = inputKeys[i];

        BIP47Wallet bip47Wallet = bip47Wallets[i];
        String paymentCode = bip47Wallet.getAccount(0).getPaymentCode();

        IMixHandler mixHandler = new MixHandler(ecKey, bip47Wallet);

        MixParams mixParams = new MixParams(utxo.getHash(), utxo.getIndex(), utxo.getValue(), paymentCode, mixHandler);
        WhirlpoolClientListener listener = computeListener();
        whirlpoolClient.whirlpool(poolId, mixParams, mixs, listener);
    }

    private WhirlpoolClientListener computeListener() {
        WhirlpoolClientListener listener = new WhirlpoolClientListener() {
            @Override
            public void success(int nbMixs) {
            }

            @Override
            public void fail(int currentMix, int nbMixs) {

            }

            @Override
            public void mixSuccess(int currentMix, int nbMixs, MixSuccess mixSuccess) {
            }

            @Override
            public void progress(int currentMix, int nbMixs, MixStatus mixStatus, int currentStep, int nbSteps) {

            }
        };
        return listener;
    }

    private void waitRegisteredInputs(int nbInputsExpected) throws Exception {
        int MAX_WAITS = 5;
        int WAIT_DURATION = 4000;
        for (int i=0; i<MAX_WAITS; i++) {
            String msg = "# ("+(i+1)+"/"+MAX_WAITS+") Waiting for registered inputs: " + mix.getNbInputs() + " vs " + nbInputsExpected;
            if (mix.getNbInputs() != nbInputsExpected) {
                log.info(msg + " : waiting longer...");
                Thread.sleep(WAIT_DURATION);
            }
            else {
                log.info(msg + " : success");
                return;
            }
        }

        // debug on failure
        log.info("# (LAST) Waiting for registered inputs: "+ mix.getNbInputs()+" vs "+nbInputsExpected);
        if (mix.getNbInputs() != nbInputsExpected) {
            debugClients();
        }
        Assert.assertEquals(nbInputsExpected, mix.getNbInputs());
    }

    public void waitLiquiditiesInPool(int nbLiquiditiesInPoolExpected) throws Exception {
        LiquidityPool liquidityPool = mix.getPool().getLiquidityPool();

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

    public void waitMixStatus(MixStatus mixStatusExpected) throws Exception {
        int MAX_WAITS = 5;
        int WAIT_DURATION = 4000;
        for (int i=0; i<MAX_WAITS; i++) {
            String msg = "# ("+(i+1)+"/"+MAX_WAITS+") Waiting for mixStatus: " + mix.getMixStatus() + " vs " + mixStatusExpected;
            if (!mix.getMixStatus().equals(mixStatusExpected)) {
                log.info(msg + " : waiting longer...");
                Thread.sleep(WAIT_DURATION);
            }
            else {
                log.info(msg + " : success");
                return;
            }
        }

        log.info("# (LAST) Waiting for mixStatus: " + mix.getMixStatus() + " vs " + mixStatusExpected);
        Assert.assertEquals(mixStatusExpected, mix.getMixStatus());
    }

    public void setMixNext() {
        Mix nextMix = mix.getPool().getCurrentMix();
        Assert.assertNotEquals(mix, nextMix);
        this.mix = nextMix;
        log.info("============= NEW MIX DETECTED: " + nextMix.getMixId() + " =============");
    }

    public void assertMixStatusRegisterInput(int nbInputsExpected, boolean hasLiquidityExpected) throws Exception {
        // wait inputs to register
        waitRegisteredInputs(nbInputsExpected);

        LiquidityPool liquidityPool = mix.getPool().getLiquidityPool();
        System.out.println("=> mixStatus="+ mix.getMixStatus()+", nbInputs="+ mix.getNbInputs());

        // all clients should have registered their outputs
        Assert.assertEquals(MixStatus.REGISTER_INPUT, mix.getMixStatus());
        Assert.assertEquals(nbInputsExpected, mix.getNbInputs());
        Assert.assertEquals(hasLiquidityExpected, liquidityPool.hasLiquidity());
    }

    public void assertMixStatusSuccess(int nbAllRegisteredExpected, boolean hasLiquidityExpected) throws Exception {
        assertMixStatusSuccess(nbAllRegisteredExpected, hasLiquidityExpected, 1);
    }

    public void assertMixStatusSuccess(int nbAllRegisteredExpected, boolean hasLiquidityExpected, int numMix) throws Exception {
        // wait inputs to register
        waitRegisteredInputs(nbAllRegisteredExpected);

        Thread.sleep(2000);

        // mix automatically switches to REGISTER_OUTPUTS, then SIGNING, then SUCCESS
        waitMixStatus(MixStatus.SUCCESS);
        Assert.assertEquals(MixStatus.SUCCESS, mix.getMixStatus());
        Assert.assertEquals(nbAllRegisteredExpected, mix.getNbInputs());

        LiquidityPool liquidityPool = mix.getPool().getLiquidityPool();
        Assert.assertEquals(hasLiquidityExpected, liquidityPool.hasLiquidity());

        // all clients should have registered their outputs
        Assert.assertEquals(nbAllRegisteredExpected, mix.getSendAddresses().size());
        Assert.assertEquals(nbAllRegisteredExpected, mix.getReceiveAddresses().size());

        // all clients should have signed
        Assert.assertEquals(nbAllRegisteredExpected, mix.getNbSignatures());

        // all clients should be SUCCESS
        assertClientsSuccess(nbAllRegisteredExpected, numMix);
    }

    public void assertMixTx(String expectedTxHash, String expectedTxHex) {
        Transaction tx = mix.getTx();
        String txHash = tx.getHashAsString();
        String txHex = new String(Hex.encode(tx.bitcoinSerialize()));
        Assert.assertEquals(expectedTxHash, txHash);
        Assert.assertEquals(expectedTxHex, txHex);
    }

    private void assertClientsSuccess(int nbAllRegisteredExpected, int numMix) {
        // clients shoud have success status
        int nbSuccess = 0;
        for (int i = 0; i< clients.length; i++) {
            WhirlpoolClient whirlpoolClient = clients[i];
            MixClient mixClient = ((WhirlpoolClientImpl)whirlpoolClient).getMixClient(numMix);
            if (MixStatus.SUCCESS.equals(mixClient.__getMixStatusNotification().status) && mixClient.isDone()) {
                nbSuccess++;
            }
        }
        if (nbAllRegisteredExpected != nbSuccess) {
            debugClients();
        }
        Assert.assertEquals(nbAllRegisteredExpected, nbSuccess);
    }

    public void nextTargetAnonymitySetAdjustment() throws Exception {
        int targetAnonymitySetExpected = mix.getTargetAnonymitySet() - 1;
        if (targetAnonymitySetExpected < mix.getPool().getMinAnonymitySet()) {
            throw new Exception("targetAnonymitySetExpected < minAnonymitySet");
        }

        log.info("nextTargetAnonymitySetAdjustment: "+ (targetAnonymitySetExpected+1) + " -> " + targetAnonymitySetExpected);

        // simulate 9min58 elapsed... mix targetAnonymitySet should remain unchanged
        mixLimitsService.__simulateElapsedTime(mix, mix.getPool().getTimeoutAdjustAnonymitySet()-2);
        Thread.sleep(1000);
        Assert.assertEquals(targetAnonymitySetExpected + 1, mix.getTargetAnonymitySet());

        // a few seconds more, mix targetAnonymitySet should be decreased
        waitMixTargetAnonymitySet(targetAnonymitySetExpected);
    }

    public void waitMixTargetAnonymitySet(int targetAnonymitySetExpected) throws Exception {
        int MAX_WAITS = 5;
        int WAIT_DURATION = 1000;
        for (int i=0; i<MAX_WAITS; i++) {
            String msg = "# ("+(i+1)+"/"+MAX_WAITS+") Waiting for mixTargetAnonymitySet: " + mix.getTargetAnonymitySet() + " vs " + targetAnonymitySetExpected;
            if (mix.getTargetAnonymitySet() != targetAnonymitySetExpected) {
                log.info(msg + " : waiting longer...");
                Thread.sleep(WAIT_DURATION);
            }
            else {
                log.info(msg + " : success");
                return;
            }
        }

        log.info("# (LAST) Waiting for mixTargetAnonymitySet: " + mix.getTargetAnonymitySet() + " vs " + targetAnonymitySetExpected);
        Assert.assertEquals(targetAnonymitySetExpected, mix.getTargetAnonymitySet());
    }

    public void exit() {
        for (WhirlpoolClient whirlpoolClient : clients) {
            if (whirlpoolClient != null) {
                whirlpoolClient.exit();
            }
        }
    }

    private void debugClients() {
        if (log.isDebugEnabled()) {
            log.debug("%%% debugging clients states... %%%");
            for (WhirlpoolClient whirlpoolClient : clients) {
                if (whirlpoolClient != null) {
                    ((WhirlpoolClientImpl)whirlpoolClient).debugState();
                }
            }
        }
    }
}
