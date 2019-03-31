package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.beans.FailReason;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.utils.timeout.ITimeoutWatcherListener;
import com.samourai.whirlpool.server.utils.timeout.TimeoutWatcher;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MixLimitsService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private MixService mixService;;
  private PoolService poolService;
  private BlameService blameService;
  private WhirlpoolServerConfig whirlpoolServerConfig;

  private Map<String, TimeoutWatcher> limitsWatchers;
  private Map<String, TimeoutWatcher> liquidityWatchers;

  @Autowired
  public MixLimitsService(
      PoolService poolService,
      BlameService blameService,
      WhirlpoolServerConfig whirlpoolServerConfig) {
    this.poolService = poolService;
    this.blameService = blameService;
    this.whirlpoolServerConfig = whirlpoolServerConfig;

    this.__reset();
  }

  // avoids circular reference
  public void setMixService(MixService mixService) {
    this.mixService = mixService;
  }

  private TimeoutWatcher getLimitsWatcher(Mix mix) {
    String mixId = mix.getMixId();
    return limitsWatchers.get(mixId);
  }

  private TimeoutWatcher getLiquidityWatcher(Mix mix) {
    String mixId = mix.getMixId();
    return liquidityWatchers.get(mixId);
  }

  public void unmanage(Mix mix) {
    String mixId = mix.getMixId();

    TimeoutWatcher limitsWatcher = getLimitsWatcher(mix);
    if (limitsWatcher != null) {
      limitsWatcher.stop();
      limitsWatchers.remove(mixId);
    }

    TimeoutWatcher liquidityWatcher = getLiquidityWatcher(mix);
    if (liquidityWatcher != null) {
      liquidityWatcher.stop();
      liquidityWatchers.remove(mixId);
    }
  }

  public void onMixStatusChange(Mix mix) {
    // reset timeout for new mixStatus
    TimeoutWatcher limitsWatcher = getLimitsWatcher(mix);
    if (limitsWatcher != null) { // may be null for tests
      limitsWatcher.resetTimeout();
    }

    // clear liquidityWatcher when CONFIRM_INPUT completed
    if (!MixStatus.CONFIRM_INPUT.equals(mix.getMixStatus())) {
      TimeoutWatcher liquidityWatcher = getLiquidityWatcher(mix);
      if (liquidityWatcher != null) {
        String mixId = mix.getMixId();

        liquidityWatcher.stop();
        liquidityWatchers.remove(mixId);
      }
    }
  }

  private TimeoutWatcher computeLimitsWatcher(Mix mix) {
    ITimeoutWatcherListener listener =
        new ITimeoutWatcherListener() {
          @Override
          public Long computeTimeToWait(TimeoutWatcher timeoutWatcher) {
            long elapsedTime = timeoutWatcher.computeElapsedTime();

            Long timeToWait = null;
            switch (mix.getMixStatus()) {
              case CONFIRM_INPUT:
                if (mix.getTargetAnonymitySet() > mix.getPool().getMinAnonymitySet()) {
                  // timeout before next targetAnonymitySet adjustment
                  timeToWait = mix.getPool().getTimeoutAdjustAnonymitySet() * 1000 - elapsedTime;
                }
                break;

              case REGISTER_OUTPUT:
                timeToWait =
                    whirlpoolServerConfig.getRegisterOutput().getTimeout() * 1000 - elapsedTime;
                break;

              case SIGNING:
                timeToWait = whirlpoolServerConfig.getSigning().getTimeout() * 1000 - elapsedTime;
                break;

              case REVEAL_OUTPUT:
                timeToWait =
                    whirlpoolServerConfig.getRevealOutput().getTimeout() * 1000 - elapsedTime;
                break;

              default:
                // no timer
                break;
            }
            return timeToWait;
          }

          @Override
          public void onTimeout(TimeoutWatcher timeoutWatcher) {
            if (log.isDebugEnabled()) {
              log.debug("limitsWatcher.onTimeout");
            }
            switch (mix.getMixStatus()) {
              case CONFIRM_INPUT:
                // adjust targetAnonymitySet
                adjustTargetAnonymitySet(mix, timeoutWatcher);
                break;

              case REGISTER_OUTPUT:
                mixService.onTimeoutRegisterOutput(mix);
                break;

              case REVEAL_OUTPUT:
                mixService.onTimeoutRevealOutput(mix);
                break;

              case SIGNING:
                blameForSigningAndResetMix(mix);
                break;
            }
          }
        };

    TimeoutWatcher mixLimitsWatcher = new TimeoutWatcher(listener);
    return mixLimitsWatcher;
  }

  private TimeoutWatcher computeLiquidityWatcher(Mix mix) {
    ITimeoutWatcherListener listener =
        new ITimeoutWatcherListener() {
          @Override
          public Long computeTimeToWait(TimeoutWatcher timeoutWatcher) {
            long elapsedTime = timeoutWatcher.computeElapsedTime();
            long timeToWait = mix.getPool().getLiquidityTimeout() * 1000 - elapsedTime;
            return timeToWait;
          }

          @Override
          public void onTimeout(TimeoutWatcher timeoutWatcher) {
            if (log.isDebugEnabled()) {
              log.debug("liquidityWatcher.onTimeout");
            }
            if (MixStatus.CONFIRM_INPUT.equals(mix.getMixStatus()) && !mix.isAcceptLiquidities()) {
              // accept liquidities
              if (log.isDebugEnabled()) {
                log.debug("accepting liquidities now (liquidityTimeout elapsed)");
              }
              mix.setAcceptLiquidities(true);
              addLiquidities(mix);
            }
            timeoutWatcher.stop();
          }
        };

    TimeoutWatcher mixLimitsWatcher = new TimeoutWatcher(listener);
    return mixLimitsWatcher;
  }

  // CONFIRM_INPUT

  private void adjustTargetAnonymitySet(Mix mix, TimeoutWatcher timeoutWatcher) {
    // no input registered yet => nothing to do
    if (mix.getNbInputs() == 0) {
      return;
    }

    // anonymitySet already at minimum
    if (mix.getPool().getMinAnonymitySet() >= mix.getTargetAnonymitySet()) {
      return;
    }

    // adjust mustMix
    int nextTargetAnonymitySet = mix.getTargetAnonymitySet() - 1;
    log.info(
        " • must-mix-adjust-timeout over, adjusting targetAnonymitySet: " + nextTargetAnonymitySet);
    mix.setTargetAnonymitySet(nextTargetAnonymitySet);
    timeoutWatcher.resetTimeout();

    // is mix ready now?
    if (mixService.isRegisterInputReady(mix)) {
      // add liquidities first
      checkAddLiquidities(mix);

      // notify mixService - which doesn't know targetAnonymitySet was adjusted
      mixService.checkConfirmInputReady(mix);
    }
  }

  public synchronized void onInputConfirmed(Mix mix) {
    // first mustMix registered => instanciate limitsWatcher & liquidityWatcher
    if (mix.getNbInputs() == 1) {
      String mixId = mix.getMixId();
      TimeoutWatcher limitsWatcher = computeLimitsWatcher(mix);
      this.limitsWatchers.put(mixId, limitsWatcher);

      TimeoutWatcher liquidityWatcher = computeLiquidityWatcher(mix);
      this.liquidityWatchers.put(mixId, liquidityWatcher);
    }

    // maybe we can add liquidities now
    checkAddLiquidities(mix);
  }

  private void checkAddLiquidities(Mix mix) {
    // avoid concurrent liquidity management
    if (mix.getNbInputsLiquidities() == 0) {

      if (!mix.isAcceptLiquidities()) {
        if (mixService.isRegisterInputReady(mix)) {
          // mix is ready to start => add liquidities now (up to maxAnonymitySet)

          if (log.isDebugEnabled()) {
            log.debug("adding liquidities now (mix is ready to start)");
          }
          mix.setAcceptLiquidities(true);
          addLiquidities(mix);
        }
      } else {
        if (mix.hasMinMustMixReached()) {
          // mix is not ready to start but minMustMix reached and liquidities accepted => add
          // liquidities now
          if (log.isDebugEnabled()) {
            log.debug("adding liquidities now (minMustMix reached and acceptLiquidities=true)");
          }
          addLiquidities(mix);
        }
      }
    }
  }

  private void addLiquidities(Mix mix) {
    if (!mix.hasMinMustMixReached()) {
      // will retry to add on onInputConfirmed
      log.info("Cannot add liquidities yet, minMustMix not reached");
      return;
    }

    int liquiditiesToAdd = mix.getPool().getMaxAnonymitySet() - mix.getNbInputs();
    if (liquiditiesToAdd > 0) {
      // add queued liquidities if any
      poolService.inviteAllToMix(mix, true);
    } else {
      if (log.isDebugEnabled()) {
        log.debug(
            "No liquidity to add: maxAnonymitySet="
                + mix.getPool().getMaxAnonymitySet()
                + ", nbInputs="
                + mix.getNbInputs());
      }
    }
  }

  public void blameForSigningAndResetMix(Mix mix) {
    log.info(" • SIGNING time over (mix failed, blaming users who didn't sign...)");
    String mixId = mix.getMixId();

    // blame users who didn't sign
    Set<ConfirmedInput> confirmedInputsToBlame =
        mix.getInputs()
            .parallelStream()
            .filter(input -> !mix.getSignedByUsername(input.getRegisteredInput().getUsername()))
            .collect(Collectors.toSet());
    List<String> outpointKeysToBlame = new ArrayList<>();
    for (ConfirmedInput confirmedInputToBlame : confirmedInputsToBlame) {
      blameService.blame(confirmedInputToBlame, BlameReason.NO_SIGNING, mixId);
      outpointKeysToBlame.add(confirmedInputToBlame.getRegisteredInput().getOutPoint().toKey());
    }

    // reset mix
    String outpointKeysToBlameStr = StringUtils.join(outpointKeysToBlame, ";");
    mixService.goFail(mix, FailReason.FAIL_SIGNING, outpointKeysToBlameStr);
  }

  public Long getLimitsWatcherTimeToWait(Mix mix) {
    TimeoutWatcher limitsWatcher = getLimitsWatcher(mix);
    if (limitsWatcher != null) {
      return limitsWatcher.computeTimeToWait();
    }
    return null;
  }

  public Long getLimitsWatcherElapsedTime(Mix mix) {
    TimeoutWatcher limitsWatcher = getLimitsWatcher(mix);
    if (limitsWatcher != null) {
      return limitsWatcher.computeElapsedTime();
    }
    return null;
  }

  public void __simulateElapsedTime(Mix mix, long elapsedTimeSeconds) {
    String mixId = mix.getMixId();
    log.info("__simulateElapsedTime for mixId=" + mixId);
    TimeoutWatcher limitsWatcher = getLimitsWatcher(mix);
    limitsWatcher.__simulateElapsedTime(elapsedTimeSeconds);

    TimeoutWatcher liquidityWatcher = getLiquidityWatcher(mix);
    if (liquidityWatcher != null) {
      liquidityWatcher.__simulateElapsedTime(elapsedTimeSeconds);
    }
  }

  public void __reset() {
    if (liquidityWatchers != null) {
      liquidityWatchers.values().forEach(watcher -> watcher.stop());
    }
    if (limitsWatchers != null) {
      limitsWatchers.values().forEach(watcher -> watcher.stop());
    }

    this.limitsWatchers = new ConcurrentHashMap<>();
    this.liquidityWatchers = new ConcurrentHashMap<>();
  }
}
