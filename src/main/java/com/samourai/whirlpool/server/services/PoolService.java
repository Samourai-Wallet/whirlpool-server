package com.samourai.whirlpool.server.services;

import com.google.common.collect.ImmutableMap;
import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.messages.SubscribePoolResponse;
import com.samourai.whirlpool.protocol.websocket.notifications.ConfirmInputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.utils.MessageListener;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
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
  private ExportService exportService;
  private Map<String, Pool> pools;

  @Autowired
  public PoolService(
      WhirlpoolServerConfig whirlpoolServerConfig,
      CryptoService cryptoService,
      WebSocketService webSocketService,
      ExportService exportService,
      WebSocketSessionService webSocketSessionService) {
    this.whirlpoolServerConfig = whirlpoolServerConfig;
    this.cryptoService = cryptoService;
    this.webSocketService = webSocketService;
    this.exportService = exportService;
    __reset();

    // listen websocket onDisconnect
    webSocketSessionService.addOnDisconnectListener(
        new MessageListener<String>() {
          @Override
          public void onMessage(String username) {
            onClientDisconnect(username);
          }
        });
  }

  public void __reset() {
    WhirlpoolServerConfig.PoolConfig[] poolConfigs = whirlpoolServerConfig.getPools();
    __reset(poolConfigs);
  }

  public void __reset(WhirlpoolServerConfig.PoolConfig[] poolConfigs) {
    pools = new ConcurrentHashMap<>();
    for (WhirlpoolServerConfig.PoolConfig poolConfig : poolConfigs) {
      String poolId = poolConfig.getId();
      long denomination = poolConfig.getDenomination();
      long feeValue = poolConfig.getFeeValue();
      Map<Long, Long> feeAccept = poolConfig.getFeeAccept();
      long minerFeeMin = poolConfig.getMinerFeeMin();
      long minerFeeCap = poolConfig.getMinerFeeCap();
      long minerFeeMax = poolConfig.getMinerFeeMax();
      long minerFeeMix = poolConfig.getMinerFeeMix();
      int minMustMix = poolConfig.getMustMixMin();
      int minLiquidity = poolConfig.getLiquidityMin();
      int anonymitySet = poolConfig.getAnonymitySet();

      Assert.notNull(poolId, "Pool configuration: poolId must not be NULL");
      Assert.isTrue(!pools.containsKey(poolId), "Pool configuration: poolId must not be duplicate");
      PoolFee poolFee = new PoolFee(feeValue, feeAccept);
      Pool pool =
          new Pool(
              poolId,
              denomination,
              poolFee,
              minerFeeMin,
              minerFeeCap,
              minerFeeMax,
              minerFeeMix,
              minMustMix,
              minLiquidity,
              anonymitySet);
      pools.put(poolId, pool);
    }
  }

  public Collection<Pool> getPools() {
    return pools.values();
  }

  public Pool getPool(String poolId) throws IllegalInputException {
    Pool pool = pools.get(poolId);
    if (pool == null) {
      throw new IllegalInputException("Pool not found");
    }
    return pool;
  }

  public SubscribePoolResponse computeSubscribePoolResponse(String poolId)
      throws IllegalInputException {
    Pool pool = getPool(poolId);
    SubscribePoolResponse poolStatusNotification =
        new SubscribePoolResponse(
            cryptoService.getNetworkParameters().getPaymentProtocolId(),
            pool.getDenomination(),
            pool.computeMustMixBalanceMin(),
            pool.computeMustMixBalanceCap(),
            pool.computeMustMixBalanceMax());
    return poolStatusNotification;
  }

  public synchronized RegisteredInput registerInput(
      String poolId,
      String username,
      boolean liquidity,
      TxOutPoint txOutPoint,
      boolean inviteIfPossible,
      String ip,
      String lastUserHash)
      throws NotifiableException {
    Pool pool = getPool(poolId);

    // verify balance
    long inputBalance = txOutPoint.getValue();
    if (!pool.checkInputBalance(inputBalance, liquidity)) {
      long balanceMin = pool.computePremixBalanceMin(liquidity);
      long balanceMax = pool.computePremixBalanceMax(liquidity);
      throw new IllegalInputException(
          "Invalid input balance (expected: "
              + balanceMin
              + "-"
              + balanceMax
              + ", actual:"
              + txOutPoint.getValue()
              + ")");
    }

    RegisteredInput registeredInput =
        new RegisteredInput(username, liquidity, txOutPoint, ip, lastUserHash);

    // verify confirmations
    if (!isUtxoConfirmed(txOutPoint, liquidity)) {
      throw new IllegalInputException("Input is not confirmed");
    }

    Mix currentMix = pool.getCurrentMix();
    if (inviteIfPossible
        && !liquidity
        && MixStatus.CONFIRM_INPUT.equals(currentMix.getMixStatus())) {
      // directly invite mustMix to mix
      inviteToMix(currentMix, registeredInput);
    } else {
      // enqueue mustMix/liquidity in pool
      queueToPool(pool, registeredInput);
    }

    return registeredInput;
  }

  private void queueToPool(Pool pool, RegisteredInput registeredInput) throws NotifiableException {
    InputPool queue;
    if (registeredInput.isLiquidity()) {
      // liquidity
      queue = pool.getLiquidityQueue();
    } else {
      // mustMix
      queue = pool.getMustMixQueue();
    }

    log.info(
        "["
            + pool.getPoolId()
            + "] "
            + registeredInput.getUsername()
            + " queueing to pool "
            + (registeredInput.isLiquidity() ? "liquidity" : "mustMix"));

    // queue input
    queue.register(registeredInput);
  }

  private void inviteToMix(Mix mix, RegisteredInput registeredInput) throws NotifiableException {
    log.info(
        "["
            + mix.getMixId()
            + "] "
            + registeredInput.getUsername()
            + " inviting "
            + (registeredInput.isLiquidity() ? "liquidity" : "mustMix")
            + " to mix: "
            + registeredInput.getOutPoint());

    // register confirming input
    String publicKey64 = WhirlpoolProtocol.encodeBytes(mix.getPublicKey());
    ConfirmInputMixStatusNotification confirmInputMixStatusNotification =
        new ConfirmInputMixStatusNotification(mix.getMixId(), publicKey64);
    mix.registerConfirmingInput(registeredInput);

    // send invite to mix
    webSocketService.sendPrivate(registeredInput.getUsername(), confirmInputMixStatusNotification);
  }

  public int inviteToMixAll(Mix mix, boolean liquidity, MixService mixService) {
    return inviteToMix(mix, liquidity, null, mixService);
  }

  public synchronized int inviteToMix(
      Mix mix, boolean liquidity, Integer maxInvites, MixService mixService) {
    Predicate<Map.Entry<String, RegisteredInput>> filterInputMixable =
        mixService.computeFilterInputMixable(mix);
    InputPool queue =
        (liquidity ? mix.getPool().getLiquidityQueue() : mix.getPool().getMustMixQueue());
    int nbInvited = 0;
    while (true) {
      // stop when enough invites
      if (maxInvites != null && nbInvited >= maxInvites) {
        break;
      }

      // stop when no more input to invite
      Optional<RegisteredInput> registeredInput = queue.removeRandom(filterInputMixable);
      if (!registeredInput.isPresent()) {
        break;
      }

      // invite one more
      try {
        inviteToMix(mix, registeredInput.get());
        nbInvited++;
      } catch (Exception e) {
        log.error("inviteToMix failed", e);
      }
    }
    if (nbInvited > 0) {
      if (log.isDebugEnabled()) {
        log.debug(
            "["
                + mix.getMixId()
                + "] invited "
                + nbInvited
                + "/"
                + (maxInvites != null ? maxInvites : "all")
                + " liquidities");
      }
    }
    return nbInvited;
  }

  public void resetLastUserHash(Mix mix) {
    mix.getPool().getLiquidityQueue().resetLastUserHash();
    mix.getPool().getMustMixQueue().resetLastUserHash();
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

  private void onClientDisconnect(String username) {
    Map<String, String> clientDetails = ImmutableMap.of("u", username);

    for (Pool pool : getPools()) {
      // remove queued liquidity
      Optional<RegisteredInput> liquidityRemoved =
          pool.getLiquidityQueue().removeByUsername(username);
      if (liquidityRemoved.isPresent()) {
        log.info("[" + pool.getPoolId() + "] " + username + " removed 1 liquidity from pool");

        // log activity
        ActivityCsv activityCsv =
            new ActivityCsv(
                "DISCONNECT", pool.getPoolId(), liquidityRemoved.get(), null, clientDetails);
        exportService.exportActivity(activityCsv);
      }

      // remove queued mustMix
      Optional<RegisteredInput> mustMixRemoved = pool.getMustMixQueue().removeByUsername(username);
      if (mustMixRemoved.isPresent()) {
        log.info("[" + pool.getPoolId() + "] " + username + " removed 1 mustMix from pool");

        // log activity
        ActivityCsv activityCsv =
            new ActivityCsv(
                "DISCONNECT", pool.getPoolId(), mustMixRemoved.get(), null, clientDetails);
        exportService.exportActivity(activityCsv);
      }
    }
  }
}
