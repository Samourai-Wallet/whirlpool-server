package com.samourai.whirlpool.server.integration;

import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.whirlpool.client.services.ClientCryptoService;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.MixException;
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

import javax.sound.sampled.Mixer;
import java.lang.invoke.MethodHandles;

public abstract class AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Value("${local.server.port}")
    protected int port;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Autowired
    protected WhirlpoolServerConfig serverConfig;

    @Autowired
    protected CryptoService cryptoService;

    protected ClientCryptoService clientCryptoService = new ClientCryptoService();

    @Autowired
    protected DbService dbService;

    @Autowired
    protected PoolService poolService;

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
        mixService.__setUseDeterministPaymentCodeMatching(true);
        mixLimitsService = mixService.__getMixLimitsService();
        ((MockBlockchainDataService)blockchainDataService).resetMock();

        configurePools(serverConfig.getPools());
    }

    protected void configurePools(WhirlpoolServerConfig.PoolConfig... poolConfigs) {
        poolService.__reset(poolConfigs);
        mixService.__reset();
    }

    protected Mix __nextMix(String mixId, WhirlpoolServerConfig.PoolConfig poolConfig) throws MixException {
        configurePools(poolConfig);
        Pool pool = poolService.getPool(poolConfig.getId());
        return mixService.__nextMix(pool, mixId);
    }

    protected Mix __nextMix(String mixId, long denomination, long minerFeeMin, long minerFeeMax, int mustMixMin, int anonymitySetTarget, int anonymitySetMin, int anonymitySetMax, long anonymitySetAdjustTimeout, long liquidityTimeout) throws MixException {
        WhirlpoolServerConfig.PoolConfig poolConfig = new WhirlpoolServerConfig.PoolConfig();
        poolConfig.setId(mixId);
        poolConfig.setDenomination(denomination);
        poolConfig.setMinerFeeMin(minerFeeMin);
        poolConfig.setMinerFeeMax(minerFeeMax);
        poolConfig.setMustMixMin(mustMixMin);
        poolConfig.setAnonymitySetTarget(anonymitySetTarget);
        poolConfig.setAnonymitySetMin(anonymitySetMin);
        poolConfig.setAnonymitySetMax(anonymitySetMax);
        poolConfig.setAnonymitySetAdjustTimeout(anonymitySetAdjustTimeout);
        poolConfig.setLiquidityTimeout(liquidityTimeout);
        return __nextMix(mixId, poolConfig);
    }

    protected Mix __getCurrentMix() {
        Pool pool = poolService.getPools().iterator().next();
        return pool.getCurrentMix();
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