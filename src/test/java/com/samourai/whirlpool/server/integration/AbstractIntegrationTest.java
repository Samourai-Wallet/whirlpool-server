package com.samourai.whirlpool.server.integration;

import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.client.services.ClientCryptoService;
import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.services.*;
import com.samourai.whirlpool.server.utils.LogbackUtils;
import com.samourai.whirlpool.server.utils.MessageSignUtil;
import com.samourai.whirlpool.server.utils.MultiClientManager;
import com.samourai.whirlpool.server.utils.TestUtils;
import org.apache.log4j.Level;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;

import java.lang.invoke.MethodHandles;

public abstract class AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Value("${local.server.port}")
    protected int port;

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
    protected BlockchainDataService blockchainDataService;

    @Autowired
    protected TestUtils testUtils;

    @Autowired
    protected BIP47Util bip47Util;

    @Autowired
    protected FormatsUtil formatsUtil;

    protected MessageSignUtil messageSignUtil;

    @Autowired
    protected TaskExecutor taskExecutor;

    protected RoundLimitsManager roundLimitsManager;

    private MultiClientManager multiClientManager;

    @Before
    public void setUp() throws Exception {
        // enable debug
        LogbackUtils.setLogLevel("com.samourai.whirlpool", Level.DEBUG.toString());

        Assert.assertTrue(blockchainDataService.testConnectivity());

        messageSignUtil = MessageSignUtil.getInstance(cryptoService.getNetworkParameters());

        dbService.__reset();
        roundService.__nextRound();
        roundService.__setUseDeterministPaymentCodeMatching(true);
        ((TestBlockchainDataService)blockchainDataService).resetMock();

        roundLimitsManager = roundService.__getRoundLimitsManager();
    }

    @After
    public void tearDown() {
        if (multiClientManager != null) {
            multiClientManager.exit();
        }
    }

    protected MultiClientManager multiClientManager(int nbClients, Round round) {
        multiClientManager = new MultiClientManager(nbClients, round, testUtils, cryptoService, blockchainDataService, roundLimitsManager, port);
        return multiClientManager;
    }

}