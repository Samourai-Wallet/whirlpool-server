package com.samourai.whirlpool.server.integration;

import com.samourai.wallet.bip47.rpc.impl.Bip47Util;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.whirlpool.client.utils.ClientCryptoService;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.services.BlockchainDataService;
import com.samourai.whirlpool.server.services.BlockchainService;
import com.samourai.whirlpool.server.services.CacheService;
import com.samourai.whirlpool.server.services.CryptoService;
import com.samourai.whirlpool.server.services.DbService;
import com.samourai.whirlpool.server.services.MixLimitsService;
import com.samourai.whirlpool.server.services.MixService;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.Tx0Service;
import com.samourai.whirlpool.server.services.rpc.MockRpcClientServiceImpl;
import com.samourai.whirlpool.server.utils.AssertMultiClientManager;
import com.samourai.whirlpool.server.utils.MessageSignUtil;
import com.samourai.whirlpool.server.utils.TestUtils;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.NetworkParameters;
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
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles(Utils.PROFILE_TEST)
public abstract class AbstractIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Value("${server.port}")
  protected int port;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Autowired protected WhirlpoolServerConfig serverConfig;

  @Autowired protected CryptoService cryptoService;

  protected ClientCryptoService clientCryptoService = new ClientCryptoService();

  @Autowired protected DbService dbService;

  @Autowired protected PoolService poolService;

  @Autowired protected MixService mixService;

  @Autowired protected BlockchainService blockchainService;

  @Autowired protected BlockchainDataService blockchainDataService;

  @Autowired protected MockRpcClientServiceImpl rpcClientService;

  @Autowired protected TestUtils testUtils;

  @Autowired protected Bech32UtilGeneric bech32Util;

  protected Bip47Util bip47Util = Bip47Util.getInstance();

  @Autowired protected FormatsUtilGeneric formatsUtil;

  @Autowired protected TaskExecutor taskExecutor;

  @Autowired protected Tx0Service tx0Service;

  @Autowired protected CacheService cacheService;

  protected MessageSignUtil messageSignUtil;

  protected MixLimitsService mixLimitsService;

  private AssertMultiClientManager multiClientManager;

  protected NetworkParameters params;

  @Before
  public void setUp() throws Exception {
    // enable debug
    Utils.setLoggerDebug("com.samourai.whirlpool");

    Assert.assertTrue(MockRpcClientServiceImpl.class.isAssignableFrom(rpcClientService.getClass()));
    this.params = cryptoService.getNetworkParameters();

    messageSignUtil = MessageSignUtil.getInstance(params);

    dbService.__reset();
    mixLimitsService = mixService.__getMixLimitsService();
    rpcClientService.resetMock();

    configurePools(serverConfig.getPools());
    cacheService._reset();
  }

  protected void configurePools(WhirlpoolServerConfig.PoolConfig... poolConfigs) {
    poolService.__reset(poolConfigs);
    mixService.__reset();
  }

  protected Mix __nextMix(WhirlpoolServerConfig.PoolConfig poolConfig)
      throws IllegalInputException {
    configurePools(poolConfig);
    Pool pool = poolService.getPool(poolConfig.getId());
    return mixService.__nextMix(pool);
  }

  protected Mix __nextMix(
      long denomination,
      long minerFeeMin,
      long minerFeeMax,
      int mustMixMin,
      int anonymitySetTarget,
      int anonymitySetMin,
      int anonymitySetMax,
      long anonymitySetAdjustTimeout,
      long liquidityTimeout)
      throws IllegalInputException {
    // create new pool
    WhirlpoolServerConfig.PoolConfig poolConfig = new WhirlpoolServerConfig.PoolConfig();
    poolConfig.setId(Utils.generateUniqueString());
    poolConfig.setDenomination(denomination);
    poolConfig.setMinerFeeMin(minerFeeMin);
    poolConfig.setMinerFeeMax(minerFeeMax);
    poolConfig.setMustMixMin(mustMixMin);
    poolConfig.setAnonymitySetTarget(anonymitySetTarget);
    poolConfig.setAnonymitySetMin(anonymitySetMin);
    poolConfig.setAnonymitySetMax(anonymitySetMax);
    poolConfig.setAnonymitySetAdjustTimeout(anonymitySetAdjustTimeout);
    poolConfig.setLiquidityTimeout(liquidityTimeout);

    // run new mix for the pool
    return __nextMix(poolConfig);
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

  protected AssertMultiClientManager multiClientManager(int nbClients, Mix mix) {
    multiClientManager =
        new AssertMultiClientManager(
            nbClients,
            mix,
            testUtils,
            cryptoService,
            rpcClientService,
            mixLimitsService,
            bip47Util,
            port);
    multiClientManager.setTestMode(serverConfig.isTestMode());
    multiClientManager.setSsl(false);
    return multiClientManager;
  }
}
