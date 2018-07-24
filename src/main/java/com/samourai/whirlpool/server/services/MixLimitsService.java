package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.v1.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.MixException;
import com.samourai.whirlpool.server.utils.timeout.ITimeoutWatcherListener;
import com.samourai.whirlpool.server.utils.timeout.TimeoutWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MixLimitsService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private MixService mixService;
    private BlameService blameService;
    private WhirlpoolServerConfig whirlpoolServerConfig;

    private Map<String, LiquidityPool> liquidityPools;
    private Map<String, TimeoutWatcher> limitsWatchers;
    private Map<String, TimeoutWatcher> liquidityWatchers;

    @Autowired
    public MixLimitsService(BlameService blameService, WhirlpoolServerConfig whirlpoolServerConfig) {
        this.blameService = blameService;
        this.whirlpoolServerConfig = whirlpoolServerConfig;

        this.liquidityPools = new HashMap<>();
        this.limitsWatchers = new HashMap<>();
        this.liquidityWatchers = new HashMap<>();
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

    public void manage(Mix mix) {
        String mixId = mix.getMixId();

        // create liquidityPool
        if (liquidityPools.containsKey(mixId)) {
            log.error("already managing mix "+mixId);
            return;
        }

        LiquidityPool liquidityPool = new LiquidityPool();
        liquidityPools.put(mixId, liquidityPool);

        // wait first mustMix before instanciating limitsWatcher & liquidityWatchers
    }

    public void unmanage(Mix mix) {
        String mixId = mix.getMixId();
        liquidityPools.remove(mixId);

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
        limitsWatcher.resetTimeout();

        // clear liquidityWatcher when REGISTER_INPUT completed
        if (!MixStatus.REGISTER_INPUT.equals(mix.getMixStatus())) {
            TimeoutWatcher liquidityWatcher = getLiquidityWatcher(mix);
            if (liquidityWatcher != null) {
                String mixId = mix.getMixId();

                liquidityWatcher.stop();
                liquidityWatchers.remove(mixId);
            }
        }
    }

    private TimeoutWatcher computeLimitsWatcher(Mix mix) {
        ITimeoutWatcherListener listener = new ITimeoutWatcherListener() {
            @Override
            public Long computeTimeToWait(TimeoutWatcher timeoutWatcher) {
                long elapsedTime = timeoutWatcher.computeElapsedTime();

                Long timeToWait = null;
                switch(mix.getMixStatus()) {
                    case REGISTER_INPUT:
                        if (mix.getTargetAnonymitySet() > mix.getMinAnonymitySet()) {
                            // timeout before next targetAnonymitySet adjustment
                            timeToWait = mix.getTimeoutAdjustAnonymitySet()*1000 - elapsedTime;
                        }
                        break;

                    case REGISTER_OUTPUT:
                        timeToWait = whirlpoolServerConfig.getRegisterOutput().getTimeout() * 1000 - elapsedTime;
                        break;

                    case SIGNING:
                        timeToWait = whirlpoolServerConfig.getSigning().getTimeout() * 1000 - elapsedTime;
                        break;

                    case REVEAL_OUTPUT:
                        timeToWait = whirlpoolServerConfig.getRevealOutput().getTimeout() * 1000 - elapsedTime;
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
                switch(mix.getMixStatus()) {
                    case REGISTER_INPUT:
                        // adjust targetAnonymitySet
                        adjustTargetAnonymitySet(mix, timeoutWatcher);
                        break;

                    case REGISTER_OUTPUT:
                        mixService.goRevealOutput(mix.getMixId());
                        break;

                    case REVEAL_OUTPUT:
                        blameForRevealOutputAndResetMix(mix);
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
        ITimeoutWatcherListener listener = new ITimeoutWatcherListener() {
            @Override
            public Long computeTimeToWait(TimeoutWatcher timeoutWatcher) {
                long elapsedTime = timeoutWatcher.computeElapsedTime();
                long timeToWait = mix.getLiquidityTimeout()*1000 - elapsedTime;
                return timeToWait;
            }

            @Override
            public void onTimeout(TimeoutWatcher timeoutWatcher) {
                if (log.isDebugEnabled()) {
                    log.debug("liquidityWatcher.onTimeout");
                }
                if (MixStatus.REGISTER_INPUT.equals(mix.getMixStatus()) && !mix.isAcceptLiquidities()) {
                    // accept liquidities
                    if (log.isDebugEnabled()) {
                        log.debug("accepting liquidities now (liquidityTimeout elapsed)");
                    }
                    mix.setAcceptLiquidities(true);
                    addLiquidities(mix);
                }
            }
        };

        TimeoutWatcher mixLimitsWatcher = new TimeoutWatcher(listener);
        return mixLimitsWatcher;
    }

    // REGISTER_INPUTS

    private void adjustTargetAnonymitySet(Mix mix, TimeoutWatcher timeoutWatcher) {
        // no input registered yet => nothing to do
        if (mix.getNbInputs() == 0) {
            return;
        }

        // anonymitySet already at minimum
        if (mix.getMinAnonymitySet() >= mix.getTargetAnonymitySet()) {
            return;
        }

        // adjust mustMix
        int nextTargetAnonymitySet = mix.getTargetAnonymitySet() - 1;
        log.info(" • must-mix-adjust-timeout over, adjusting targetAnonymitySet: "+nextTargetAnonymitySet);
        mix.setTargetAnonymitySet(nextTargetAnonymitySet);
        timeoutWatcher.resetTimeout();

        // is mix ready now?
        if (mixService.isRegisterInputReady(mix)) {
            // add liquidities first
            checkAddLiquidities(mix);

            // start mix
            mixService.checkRegisterInputReady(mix);
        }
    }

    public LiquidityPool getLiquidityPool(Mix mix) throws MixException {
        String mixId = mix.getMixId();
        LiquidityPool liquidityPool = liquidityPools.get(mixId);
        if (liquidityPool == null) {
            throw new MixException("LiquidityPool not found for mixId="+mixId);
        }
        return liquidityPool;
    }

    public synchronized void onInputRegistered(Mix mix) {
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
                    // mix is ready to start => add liquidities now

                    if (log.isDebugEnabled()) {
                        log.debug("adding liquidities now (mix is ready to start)");
                    }
                    mix.setAcceptLiquidities(true);
                    addLiquidities(mix);
                }
            }
            else {
                if (mix.hasMinMustMixReached()) {
                    // mix is not ready to start but minMustMix reached and liquidities accepted => ad liquidities now
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
            // will retry to add on onInputRegistered
            log.info("Cannot add liquidities yet, minMustMix not reached");
            return;
        }

        int liquiditiesToAdd = mix.getMaxAnonymitySet() - mix.getNbInputs();
        if (liquiditiesToAdd > 0) {
            // mix needs liquidities
            try {
                LiquidityPool liquidityPool = getLiquidityPool(mix);
                if (log.isDebugEnabled()) {
                    log.debug("Adding up to " + liquiditiesToAdd + " liquidities... ("+liquidityPool.getNbLiquidities()+" available)");
                }

                int liquiditiesAdded = 0;
                while (liquiditiesAdded < liquiditiesToAdd && liquidityPool.hasLiquidity()) {
                    log.info("Adding liquidity " + (liquiditiesAdded + 1) + "(" + liquiditiesToAdd + " max.)");
                    RegisteredLiquidity randomLiquidity = liquidityPool.peekRandomLiquidity();
                    try {
                        mixService.addLiquidity(mix, randomLiquidity);
                        liquiditiesAdded++;
                    } catch (Exception e) {
                        log.error("registerInput error when adding liquidity", e);
                        // ignore the error and continue with more liquidity
                    }
                }
            }
            catch(Exception e) {
                log.error("Unexpected exception", e);
            }
        }
    }

    public void blameForRevealOutputAndResetMix(Mix mix) {
        String mixId = mix.getMixId();

        // blame users who didn't register outputs
        Set<RegisteredInput> registeredInputsToBlame = mix.getInputs().parallelStream().filter(input -> !mix.getRevealedOutputUsers().contains(input.getUsername())).collect(Collectors.toSet());
        registeredInputsToBlame.forEach(registeredInputToBlame -> blameService.blame(registeredInputToBlame, BlameReason.NO_REGISTER_OUTPUT, mixId));

        // reset mix
        mixService.goFail(mix, FailReason.FAIL_REGISTER_OUTPUTS);
    }

    public void blameForSigningAndResetMix(Mix mix) {
        log.info(" • SIGNING time over (mix failed, blaming users who didn't sign...)");
        String mixId = mix.getMixId();

        // blame users who didn't sign
        Set<RegisteredInput> registeredInputsToBlame = mix.getInputs().parallelStream().filter(input -> mix.getSignatureByUsername(input.getUsername()) == null).collect(Collectors.toSet());
        registeredInputsToBlame.forEach(registeredInputToBlame -> blameService.blame(registeredInputToBlame, BlameReason.NO_SIGNING, mixId));

        // reset mix
        mixService.goFail(mix, FailReason.FAIL_SIGNING);
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
        log.info("__simulateElapsedTime for mixId="+mixId);
        TimeoutWatcher limitsWatcher = getLimitsWatcher(mix);
        limitsWatcher.__simulateElapsedTime(elapsedTimeSeconds);

        TimeoutWatcher liquidityWatcher = getLiquidityWatcher(mix);
        if (liquidityWatcher != null) {
            liquidityWatcher.__simulateElapsedTime(elapsedTimeSeconds);
        }
    }
}
