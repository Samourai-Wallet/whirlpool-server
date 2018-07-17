package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;
import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.RoundException;
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
public class RoundLimitsService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private RoundService roundService;
    private BlameService blameService;
    private WhirlpoolServerConfig whirlpoolServerConfig;

    private Map<String, LiquidityPool> liquidityPools;
    private Map<String, TimeoutWatcher> limitsWatchers;
    private Map<String, TimeoutWatcher> liquidityWatchers;

    @Autowired
    public RoundLimitsService(BlameService blameService, WhirlpoolServerConfig whirlpoolServerConfig) {
        this.blameService = blameService;
        this.whirlpoolServerConfig = whirlpoolServerConfig;

        this.liquidityPools = new HashMap<>();
        this.limitsWatchers = new HashMap<>();
        this.liquidityWatchers = new HashMap<>();
    }

    // avoids circular reference
    public void setRoundService(RoundService roundService) {
        this.roundService = roundService;
    }

    private TimeoutWatcher getLimitsWatcher(Round round) {
        String roundId = round.getRoundId();
        return limitsWatchers.get(roundId);
    }

    private TimeoutWatcher getLiquidityWatcher(Round round) {
        String roundId = round.getRoundId();
        return liquidityWatchers.get(roundId);
    }

    public void manage(Round round) {
        String roundId = round.getRoundId();

        // create liquidityPool
        if (liquidityPools.containsKey(roundId)) {
            log.error("already managing round "+roundId);
            return;
        }

        LiquidityPool liquidityPool = new LiquidityPool();
        liquidityPools.put(roundId, liquidityPool);

        // wait first mustMix before instanciating limitsWatcher & liquidityWatchers
    }

    public void unmanage(Round round) {
        String roundId = round.getRoundId();
        liquidityPools.remove(roundId);

        TimeoutWatcher limitsWatcher = getLimitsWatcher(round);
        if (limitsWatcher != null) {
            limitsWatcher.stop();
            limitsWatchers.remove(roundId);
        }

        TimeoutWatcher liquidityWatcher = getLiquidityWatcher(round);
        if (liquidityWatcher != null) {
            liquidityWatcher.stop();
            liquidityWatchers.remove(roundId);
        }
    }

    public void onRoundStatusChange(Round round) {
        // reset timeout for new roundStatus
        TimeoutWatcher limitsWatcher = getLimitsWatcher(round);
        limitsWatcher.resetTimeout();

        // clear liquidityWatcher when REGISTER_INPUT completed
        if (!RoundStatus.REGISTER_INPUT.equals(round.getRoundStatus())) {
            TimeoutWatcher liquidityWatcher = getLiquidityWatcher(round);
            if (liquidityWatcher != null) {
                String roundId = round.getRoundId();

                liquidityWatcher.stop();
                liquidityWatchers.remove(roundId);
            }
        }
    }

    private TimeoutWatcher computeLimitsWatcher(Round round) {
        ITimeoutWatcherListener listener = new ITimeoutWatcherListener() {
            @Override
            public Long computeTimeToWait(TimeoutWatcher timeoutWatcher) {
                long elapsedTime = timeoutWatcher.computeElapsedTime();

                Long timeToWait = null;
                switch(round.getRoundStatus()) {
                    case REGISTER_INPUT:
                        if (round.getTargetAnonymitySet() > round.getMinAnonymitySet()) {
                            // timeout before next targetAnonymitySet adjustment
                            timeToWait = round.getTimeoutAdjustAnonymitySet()*1000 - elapsedTime;
                        }
                        break;

                    case REGISTER_OUTPUT:
                        timeToWait = whirlpoolServerConfig.getRegisterOutput().getTimeout() * 1000 - elapsedTime;
                        break;

                    case SIGNING:
                        timeToWait = whirlpoolServerConfig.getSigning().getTimeout() * 1000 - elapsedTime;
                        break;

                    case REVEAL_OUTPUT_OR_BLAME:
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
                switch(round.getRoundStatus()) {
                    case REGISTER_INPUT:
                        // adjust targetAnonymitySet
                        adjustTargetAnonymitySet(round, timeoutWatcher);
                        break;

                    case REGISTER_OUTPUT:
                        roundService.goRevealOutputOrBlame(round.getRoundId());
                        break;

                    case REVEAL_OUTPUT_OR_BLAME:
                        blameForRevealOutputAndResetRound(round);
                        break;

                    case SIGNING:
                        blameForSigningAndResetRound(round);
                        break;
                }
            }
        };

        TimeoutWatcher roundLimitsWatcher = new TimeoutWatcher(listener);
        return roundLimitsWatcher;
    }

    private TimeoutWatcher computeLiquidityWatcher(Round round) {
        ITimeoutWatcherListener listener = new ITimeoutWatcherListener() {
            @Override
            public Long computeTimeToWait(TimeoutWatcher timeoutWatcher) {
                long elapsedTime = timeoutWatcher.computeElapsedTime();
                long timeToWait = round.getLiquidityTimeout()*1000 - elapsedTime;
                return timeToWait;
            }

            @Override
            public void onTimeout(TimeoutWatcher timeoutWatcher) {
                if (log.isDebugEnabled()) {
                    log.debug("liquidityWatcher.onTimeout");
                }
                if (RoundStatus.REGISTER_INPUT.equals(round.getRoundStatus()) && !round.isAcceptLiquidities()) {
                    // accept liquidities
                    if (log.isDebugEnabled()) {
                        log.debug("accepting liquidities now (liquidityTimeout elapsed)");
                    }
                    round.setAcceptLiquidities(true);
                    addLiquidities(round);
                }
            }
        };

        TimeoutWatcher roundLimitsWatcher = new TimeoutWatcher(listener);
        return roundLimitsWatcher;
    }

    // REGISTER_INPUTS

    private void adjustTargetAnonymitySet(Round round, TimeoutWatcher timeoutWatcher) {
        // no input registered yet => nothing to do
        if (round.getNbInputs() == 0) {
            return;
        }

        // anonymitySet already at minimum
        if (round.getMinAnonymitySet() >= round.getTargetAnonymitySet()) {
            return;
        }

        // adjust mustMix
        int nextTargetAnonymitySet = round.getTargetAnonymitySet() - 1;
        log.info(" • must-mix-adjust-timeout over, adjusting targetAnonymitySet: "+nextTargetAnonymitySet);
        round.setTargetAnonymitySet(nextTargetAnonymitySet);
        timeoutWatcher.resetTimeout();

        // is round ready now?
        if (roundService.isRegisterInputReady(round)) {
            // add liquidities first
            checkAddLiquidities(round);

            // start round
            roundService.checkRegisterInputReady(round);
        }
    }

    public LiquidityPool getLiquidityPool(Round round) throws RoundException {
        String roundId = round.getRoundId();
        LiquidityPool liquidityPool = liquidityPools.get(roundId);
        if (liquidityPool == null) {
            throw new RoundException("LiquidityPool not found for roundId="+roundId);
        }
        return liquidityPool;
    }

    public synchronized void onInputRegistered(Round round) {
        // first mustMix registered => instanciate limitsWatcher & liquidityWatcher
        if (round.getNbInputs() == 1) {
            String roundId = round.getRoundId();
            TimeoutWatcher limitsWatcher = computeLimitsWatcher(round);
            this.limitsWatchers.put(roundId, limitsWatcher);

            TimeoutWatcher liquidityWatcher = computeLiquidityWatcher(round);
            this.liquidityWatchers.put(roundId, liquidityWatcher);
        }

        // maybe we can add liquidities now
        checkAddLiquidities(round);
    }

    private void checkAddLiquidities(Round round) {
        // avoid concurrent liquidity management
        if (round.getNbInputsLiquidities() == 0) {

            if (!round.isAcceptLiquidities()) {
                if (roundService.isRegisterInputReady(round)) {
                    // round is ready to start => add liquidities now

                    if (log.isDebugEnabled()) {
                        log.debug("adding liquidities now (round is ready to start)");
                    }
                    round.setAcceptLiquidities(true);
                    addLiquidities(round);
                }
            }
            else {
                if (round.hasMinMustMixReached()) {
                    // round is not ready to start but minMustMix reached and liquidities accepted => ad liquidities now
                    if (log.isDebugEnabled()) {
                        log.debug("adding liquidities now (minMustMix reached and acceptLiquidities=true)");
                    }
                    addLiquidities(round);
                }
            }
        }
    }

    private void addLiquidities(Round round) {
        if (!round.hasMinMustMixReached()) {
            // will retry to add on onInputRegistered
            log.info("Cannot add liquidities yet, minMustMix not reached");
            return;
        }

        int liquiditiesToAdd = round.getMaxAnonymitySet() - round.getNbInputs();
        if (liquiditiesToAdd > 0) {
            // round needs liquidities
            try {
                LiquidityPool liquidityPool = getLiquidityPool(round);
                if (log.isDebugEnabled()) {
                    log.debug("Adding up to " + liquiditiesToAdd + " liquidities... ("+liquidityPool.getNbLiquidities()+" available)");
                }

                int liquiditiesAdded = 0;
                while (liquiditiesAdded < liquiditiesToAdd && liquidityPool.hasLiquidity()) {
                    log.info("Adding liquidity " + (liquiditiesAdded + 1) + "(" + liquiditiesToAdd + " max.)");
                    RegisteredLiquidity randomLiquidity = liquidityPool.peekRandomLiquidity();
                    try {
                        roundService.addLiquidity(round, randomLiquidity);
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

    public void blameForRevealOutputAndResetRound(Round round) {
        String roundId = round.getRoundId();

        // blame users who didn't register outputs
        Set<RegisteredInput> registeredInputsToBlame = round.getInputs().parallelStream().filter(input -> !round.getRevealedOutputUsers().contains(input.getUsername())).collect(Collectors.toSet());
        registeredInputsToBlame.forEach(registeredInputToBlame -> blameService.blame(registeredInputToBlame, BlameReason.NO_REGISTER_OUTPUT, roundId));

        // reset round
        roundService.goFail(round, FailReason.FAIL_REGISTER_OUTPUTS);
    }

    public void blameForSigningAndResetRound(Round round) {
        log.info(" • SIGNING time over (round failed, blaming users who didn't sign...)");
        String roundId = round.getRoundId();

        // blame users who didn't sign
        Set<RegisteredInput> registeredInputsToBlame = round.getInputs().parallelStream().filter(input -> round.getSignatureByUsername(input.getUsername()) == null).collect(Collectors.toSet());
        registeredInputsToBlame.forEach(registeredInputToBlame -> blameService.blame(registeredInputToBlame, BlameReason.NO_SIGNING, roundId));

        // reset round
        roundService.goFail(round, FailReason.FAIL_SIGNING);
    }

    public Long getLimitsWatcherTimeToWait(Round round) {
        TimeoutWatcher limitsWatcher = getLimitsWatcher(round);
        if (limitsWatcher != null) {
            return limitsWatcher.computeTimeToWait();
        }
        return null;
    }

    public Long getLimitsWatcherElapsedTime(Round round) {
        TimeoutWatcher limitsWatcher = getLimitsWatcher(round);
        if (limitsWatcher != null) {
            return limitsWatcher.computeElapsedTime();
        }
        return null;
    }

    public void __simulateElapsedTime(Round round, long elapsedTimeSeconds) {
        String roundId = round.getRoundId();
        log.info("__simulateElapsedTime for roundId="+roundId);
        TimeoutWatcher limitsWatcher = getLimitsWatcher(round);
        limitsWatcher.__simulateElapsedTime(elapsedTimeSeconds);

        TimeoutWatcher liquidityWatcher = getLiquidityWatcher(round);
        if (liquidityWatcher != null) {
            liquidityWatcher.__simulateElapsedTime(elapsedTimeSeconds);
        }
    }
}
