package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.messages.SubscribePoolResponse;
import com.samourai.whirlpool.protocol.websocket.notifications.ConfirmInputMixStatusNotification;
import com.samourai.whirlpool.server.beans.InputPool;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.MixException;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class PoolService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private WhirlpoolServerConfig whirlpoolServerConfig;
  private CryptoService cryptoService;
  private WebSocketService webSocketService;
  private Map<String, Pool> pools;

  @Autowired
  public PoolService(
      WhirlpoolServerConfig whirlpoolServerConfig,
      CryptoService cryptoService,
      WebSocketService webSocketService) {
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.cryptoService = cryptoService;
    this.webSocketService = webSocketService;
    __reset();
  }

  public void __reset() {
    WhirlpoolServerConfig.PoolConfig[] poolConfigs = whirlpoolServerConfig.getPools();
    __reset(poolConfigs);
  }

  public void __reset(WhirlpoolServerConfig.PoolConfig[] poolConfigs) {
    pools = new HashMap<>();
    for (WhirlpoolServerConfig.PoolConfig poolConfig : poolConfigs) {
      String poolId = poolConfig.getId();
      long denomination = poolConfig.getDenomination();
      long minerFeeMin = poolConfig.getMinerFeeMin();
      long minerFeeMax = poolConfig.getMinerFeeMax();
      int minMustMix = poolConfig.getMustMixMin();
      int targetAnonymitySet = poolConfig.getAnonymitySetTarget();
      int minAnonymitySet = poolConfig.getAnonymitySetMin();
      int maxAnonymitySet = poolConfig.getAnonymitySetMax();
      long mustMixAdjustTimeout = poolConfig.getAnonymitySetAdjustTimeout();
      long liquidityTimeout = poolConfig.getLiquidityTimeout();

      Assert.notNull(poolId, "Pool configuration: poolId must not be NULL");
      Assert.isTrue(!pools.containsKey(poolId), "Pool configuration: poolId must not be duplicate");
      Pool pool =
          new Pool(
              poolId,
              denomination,
              minerFeeMin,
              minerFeeMax,
              minMustMix,
              targetAnonymitySet,
              minAnonymitySet,
              maxAnonymitySet,
              mustMixAdjustTimeout,
              liquidityTimeout);
      pools.put(poolId, pool);
    }
  }

  public Collection<Pool> getPools() {
    return pools.values();
  }

  public Pool getPool(String poolId) throws MixException {
    Pool pool = pools.get(poolId);
    if (pool == null) {
      throw new MixException("Pool not found");
    }
    return pool;
  }

  public SubscribePoolResponse computeSubscribePoolResponse(String poolId) throws MixException {
    Pool pool = getPool(poolId);
    SubscribePoolResponse poolStatusNotification =
        new SubscribePoolResponse(
            cryptoService.getNetworkParameters().getPaymentProtocolId(),
            pool.getDenomination(),
            pool.getMinerFeeMin(),
            pool.getMinerFeeMax());
    return poolStatusNotification;
  }

  public synchronized void registerInput(
      String poolId,
      String username,
      byte[] pubkey,
      boolean liquidity,
      TxOutPoint input,
      boolean inviteIfPossible)
      throws IllegalInputException, MixException {
    Pool pool = getPool(poolId);

    // verify balance
    long inputBalance = input.getValue();
    if (!pool.checkInputBalance(inputBalance, liquidity)) {
      long balanceMin = pool.computeInputBalanceMin(liquidity);
      long balanceMax = pool.computeInputBalanceMax(liquidity);
      throw new IllegalInputException(
          "Invalid input balance (expected: "
              + balanceMin
              + "-"
              + balanceMax
              + ", actual:"
              + input.getValue()
              + ")");
    }

    RegisteredInput registeredInput = new RegisteredInput(username, pubkey, liquidity, input);

    // verify confirmations
    if (!isUtxoConfirmed(input, liquidity)) {
      log.info(
          " • ["
              + poolId
              + "] queued UNCONFIRMED UTXO "
              + (liquidity ? "liquidity" : "mustMix")
              + ": "
              + input);
      pool.getUnconfirmedQueue().register(registeredInput);
      return;
    }

    Mix currentMix = pool.getCurrentMix();
    if (inviteIfPossible && currentMix.isInvitationOpen(liquidity)) {
      // mix invitation open => directly invite to mix
      inviteToMix(currentMix, registeredInput);
    } else {
      // enqueue in pool
      queueToPool(pool, registeredInput);
    }
  }

  private void queueToPool(Pool pool, RegisteredInput registeredInput) {
    InputPool queue;
    if (registeredInput.isLiquidity()) {
      // liquidity
      queue = pool.getLiquidityQueue();
    } else {
      // mustMix
      queue = pool.getMustMixQueue();
    }

    // queue input
    queue.register(registeredInput);

    log.info(
        " • ["
            + pool.getPoolId()
            + "] queued "
            + (registeredInput.isLiquidity() ? "liquidity" : "mustMix")
            + ": "
            + registeredInput.getInput());
  }

  private void inviteToMix(Mix mix, RegisteredInput registeredInput) {
    // register confirming input
    String publicKey64 = WhirlpoolProtocol.encodeBytes(mix.getPublicKey());
    ConfirmInputMixStatusNotification confirmInputMixStatusNotification =
        new ConfirmInputMixStatusNotification(mix.getMixId(), publicKey64);
    mix.registerConfirmingInput(registeredInput);

    log.info(
        " • ["
            + mix.getMixId()
            + "] inviting "
            + (registeredInput.isLiquidity() ? "liquidity" : "mustMix")
            + " to mix: "
            + registeredInput.getInput());

    // send invite to mix
    webSocketService.sendPrivate(registeredInput.getUsername(), confirmInputMixStatusNotification);
  }

  public synchronized int inviteAllToMix(Mix mix, boolean liquidity) {
    InputPool queue =
        (liquidity ? mix.getPool().getLiquidityQueue() : mix.getPool().getMustMixQueue());
    Optional<RegisteredInput> registeredInput;
    int nbInvited = 0;
    while ((registeredInput = queue.peekRandom()).isPresent()) {
      inviteToMix(mix, registeredInput.get());
      nbInvited++;
    }
    return nbInvited;
  }

  private boolean isUtxoConfirmed(TxOutPoint txOutPoint, boolean liquidity) {
    int inputConfirmations = txOutPoint.getConfirmations();
    if (liquidity) {
      // liquidity
      int minConfirmationsMix =
          whirlpoolServerConfig.getRegisterInput().getMinConfirmationsLiquidity();
      if (inputConfirmations < minConfirmationsMix) {
        log.info(
            "input not confirmed: liquidity needs at least "
                + minConfirmationsMix
                + " confirmations: "
                + txOutPoint.getHash());
        return false;
      }
    } else {
      // mustMix
      int minConfirmationsTx0 =
          whirlpoolServerConfig.getRegisterInput().getMinConfirmationsMustMix();
      if (inputConfirmations < minConfirmationsTx0) {
        log.info(
            "input not confirmed: mustMix needs at least "
                + minConfirmationsTx0
                + " confirmations: "
                + txOutPoint.getHash());
        return false;
      }
    }
    return true;
  }

  public synchronized void onClientDisconnect(String username) {
    for (Pool pool : getPools()) {
      // remove queued liquidity
      boolean liquidityRemoved = pool.getLiquidityQueue().removeByUsername(username).isPresent();
      if (liquidityRemoved) {
        log.info(
            " • [" + pool.getPoolId() + "] removed 1 liquidity from pool, username=" + username);
      }

      // remove queued mustMix
      boolean mustMixRemoved = pool.getMustMixQueue().removeByUsername(username).isPresent();
      if (mustMixRemoved) {
        log.info(" • [" + pool.getPoolId() + "] removed 1 mustMix from pool, username=" + username);
      }

      // remove unconfirmed utxo
      boolean unconfirmedInputRemoved =
          pool.getUnconfirmedQueue().removeByUsername(username).isPresent();
      if (unconfirmedInputRemoved) {
        log.info(
            " • ["
                + pool.getPoolId()
                + "] removed 1 unconfirmed UTXO from pool, username="
                + username);
      }
    }
  }
}
