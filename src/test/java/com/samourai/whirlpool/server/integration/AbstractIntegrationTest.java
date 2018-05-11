package com.samourai.whirlpool.server.integration;

import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.services.ClientCryptoService;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.services.*;
import com.samourai.whirlpool.server.utils.MessageSignUtil;
import com.samourai.whirlpool.server.utils.TestUtils;
import org.bitcoinj.core.*;
import org.bouncycastle.util.encoders.Hex;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

public abstract class AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Value("${local.server.port}")
    private int port;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Autowired
    protected CryptoService cryptoService;

    protected ClientCryptoService clientCryptoService = new ClientCryptoService();

    @Autowired
    protected DbService dbService;

    @Autowired
    protected RoundService roundService;

    @Autowired
    protected BlockchainService blockchainService;

    @Autowired
    protected BlockchainDataService blockchainDataService;

    @Autowired
    protected TestUtils testUtils;

    @Autowired
    protected Bech32Util bech32Util;

    @Autowired
    protected BIP47Util bip47Util;

    @Autowired
    protected FormatsUtil formatsUtil;

    protected MessageSignUtil messageSignUtil;

    @Autowired
    protected TaskExecutor taskExecutor;

    protected WhirlpoolClient[] whirlpoolClients;

    protected RoundLimitsManager roundLimitsManager;

    @Before
    public void setUp() throws Exception {
        messageSignUtil = MessageSignUtil.getInstance(cryptoService.getNetworkParameters());

        dbService.__reset();
        roundService.__nextRound();
        roundService.__setUseDeterministPaymentCodeMatching(true);
        blockchainDataService.__reset();

        roundLimitsManager = roundService.__getRoundLimitsManager();
    }

    @After
    public void tearDown() throws Exception {
        if (whirlpoolClients != null) {
            for (WhirlpoolClient client : whirlpoolClients) {
                client.disconnect();
            }
        }
    }

    protected long computeSpendAmount(Round round, boolean liquidity) {
        if (liquidity) {
            // no minerFees for liquidities
            return round.getDenomination();
        }
        return round.getDenomination() + round.getFees();
    }

    protected WhirlpoolClient createClient() {
        String wsUrl = "ws://127.0.0.1:" + port;
        return new WhirlpoolClient(wsUrl, cryptoService.getNetworkParameters());
    }

    protected WhirlpoolClient[] createClients(int nbClients) {
        WhirlpoolClient[] clients = new WhirlpoolClient[nbClients];
        for (int i=0; i<nbClients; i++) {
            clients[i] = createClient();
        }
        return clients;
    }

    protected TxOutPoint createAndMockTxOutPoint(SegwitAddress address, long amount) throws Exception {
        return createAndMockTxOutPoint(address, amount, 1000, null, null);
    }

    protected TxOutPoint createAndMockTxOutPoint(SegwitAddress address, long amount, int nbConfirmations) throws Exception {
        return createAndMockTxOutPoint(address, amount, nbConfirmations, null, null);
    }

    protected TxOutPoint createAndMockTxOutPoint(SegwitAddress address, long amount, int nbConfirmations, String utxoHash, Integer utxoIndex) throws Exception{
        // generate transaction with bitcoinj
        Transaction transaction = new Transaction(cryptoService.getNetworkParameters());
        if (utxoHash != null) {
            transaction = Mockito.spy(transaction);
            Mockito.doReturn(new Sha256Hash(Hex.decode(utxoHash))).when(transaction).getHash();
        }

        if (utxoIndex != null) {
            for (int i=0; i<utxoIndex; i++) {
                transaction.addOutput(Coin.valueOf(amount), testUtils.createSegwitAddress().getAddress());
            }
        }
        String addressBech32 = address.getBech32AsString();
        TransactionOutput transactionOutput = bech32Util.getTransactionOutput(addressBech32, amount, cryptoService.getNetworkParameters());
        transaction.addOutput(transactionOutput);
        TransactionOutPoint outPoint = transactionOutput.getOutPointFor();

        // mock at rpc level
        RpcTransaction rpcTransaction = new RpcTransaction(transaction.getHashAsString(), nbConfirmations);
        RpcOut rpcOut = new RpcOut(outPoint.getIndex(), amount, outPoint.getConnectedPubKeyScript(), Arrays.asList(addressBech32));
        rpcTransaction.addRpcOut(rpcOut);
        blockchainDataService.__mock(rpcTransaction);

        TxOutPoint txOutPoint = new TxOutPoint(rpcTransaction.getHash(), rpcOut.getIndex(), rpcOut.getValue());
        return txOutPoint;
    }

    protected void simulateRoundNextTargetMustMixAdjustment(Round round, int targetMustMixExpected) throws Exception {
        // round targetMustMix should be unchanged
        Assert.assertEquals(targetMustMixExpected + 1, round.getTargetMustMix());

        // simulate 9min58 elapsed... round targetMustMix should remain unchanged
        roundLimitsManager.__simulateElapsedTime(round, round.getMustMixAdjustTimeout()-2);
        Thread.sleep(500);
        Assert.assertEquals(targetMustMixExpected + 1, round.getTargetMustMix());

        // a few seconds more, round targetMustMix should be decreased
        Thread.sleep(2000);
        Assert.assertEquals(targetMustMixExpected, round.getTargetMustMix());
    }

    protected void assertStatusSuccess(Round round, int NB_ALL_REGISTERED_EXPECTED, boolean hasLiquidityExpected) throws Exception {
        LiquidityPool liquidityPool = roundLimitsManager.getLiquidityPool(round);
        System.out.println("=> roundStatus="+round.getRoundStatus()+", nbInputs="+round.getNbInputs());

        // round automatically switches to REGISTER_OUTPUTS, then SIGNING, then SUCCESS
        Assert.assertEquals(RoundStatus.SUCCESS, round.getRoundStatus());
        Assert.assertEquals(NB_ALL_REGISTERED_EXPECTED, round.getNbInputs());
        Assert.assertEquals(hasLiquidityExpected, liquidityPool.hasLiquidity());

        // all clients should have registered their outputs
        Assert.assertEquals(NB_ALL_REGISTERED_EXPECTED, round.getSendAddresses().size());
        Assert.assertEquals(NB_ALL_REGISTERED_EXPECTED, round.getReceiveAddresses().size());

        // all clients should have signed
        Assert.assertEquals(NB_ALL_REGISTERED_EXPECTED, round.getNbSignatures());
    }

    protected void assertClientsSuccess() {
        // all clients shoud have success status
        for (int i=0; i<whirlpoolClients.length; i++) {
            Assert.assertEquals(RoundStatus.SUCCESS, whirlpoolClients[i].__getRoundStatusNotification().status);
        }
    }

    protected void assertStatusRegisterInput(Round round, int nbInputsExpected, boolean hasLiquidityExpected) throws Exception {
        LiquidityPool liquidityPool = roundLimitsManager.getLiquidityPool(round);
        System.out.println("=> roundStatus="+round.getRoundStatus()+", nbInputs="+round.getNbInputs());

        // all clients should have registered their outputs
        Assert.assertEquals(RoundStatus.REGISTER_INPUT, round.getRoundStatus());
        Assert.assertEquals(nbInputsExpected, round.getNbInputs());
        Assert.assertEquals(hasLiquidityExpected, liquidityPool.hasLiquidity());
    }

}