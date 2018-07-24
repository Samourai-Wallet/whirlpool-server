package com.samourai.whirlpool.server.integration;

import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.client.services.ClientCryptoService;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.services.*;
import com.samourai.whirlpool.server.utils.*;
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
    protected MixService mixService;

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

    protected MixLimitsService mixLimitsService;

    private MultiClientManager multiClientManager;

    @Before
    public void setUp() throws Exception {
        // enable debug
        Utils.setLoggerDebug("com.samourai.whirlpool");

        Assert.assertTrue(blockchainDataService.testConnectivity());

        messageSignUtil = MessageSignUtil.getInstance(cryptoService.getNetworkParameters());

        dbService.__reset();
        mixService.__nextMix();
        mixService.__setUseDeterministPaymentCodeMatching(true);
        ((MockBlockchainDataService)blockchainDataService).resetMock();

        mixLimitsService = mixService.__getMixLimitsService();
    }

    @After
    public void tearDown() {
        if (multiClientManager != null) {
            multiClientManager.exit();
        }
    }

    protected MultiClientManager multiClientManager(int nbClients, Mix mix) {
        multiClientManager = new MultiClientManager(nbClients, mix, mixService, testUtils, cryptoService, mixLimitsService, port);
        return multiClientManager;
    }

}