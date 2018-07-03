package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.RoundException;
import com.samourai.whirlpool.server.utils.timeout.ITimeoutWatcherListener;
import com.samourai.whirlpool.server.utils.timeout.TimeoutWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RoundLimitsManager {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private RoundService roundService;
    private BlameService blameService;
    private WhirlpoolServerConfig whirlpoolServerConfig;

    private Map<String, LiquidityPool> liquidityPools;
    private Map<String, TimeoutWatcher> roundWatchers;

    public RoundLimitsManager(RoundService roundService, BlameService blameService, WhirlpoolServerConfig whirlpoolServerConfig) {
        this.roundService = roundService;
        this.blameService = blameService;
        this.whirlpoolServerConfig = whirlpoolServerConfig;

        this.liquidityPools = new HashMap<>();
        this.roundWatchers = new HashMap<>();
    }

    private TimeoutWatcher getRoundLimitsWatcher(Round round) {
        String roundId = round.getRoundId();
        return roundWatchers.get(roundId);
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

        // wait first input registered before instanciating roundWatcher
    }

    public void unmanage(Round round) {
        String roundId = round.getRoundId();
        liquidityPools.remove(roundId);

        TimeoutWatcher roundLimitsWatcher = getRoundLimitsWatcher(round);
        if (roundLimitsWatcher != null) {
            roundLimitsWatcher.stop();
            roundWatchers.remove(roundId);
        }
    }

    public void onRoundStatusChange(Round round) {
        TimeoutWatcher roundLimitsWatcher = getRoundLimitsWatcher(round);

        // reset timeout for new roundStatus
        roundLimitsWatcher.resetTimeout();
    }

    public TimeoutWatcher computeRoundLimitsWatcher(Round round) {
        ITimeoutWatcherListener listener = new ITimeoutWatcherListener() {
            private long computeTimeToWaitAcceptLiquidities(long elapsedTime) {
                long timeToWaitAcceptLiquidities = round.getLiquidityTimeout()*1000 - elapsedTime;
                return timeToWaitAcceptLiquidities;
            }

            @Override
            public long computeTimeToWait(TimeoutWatcher timeoutWatcher) {
                long elapsedTime = timeoutWatcher.computeElapsedTime();

                long timeToWait;
                switch(round.getRoundStatus()) {
                    case REGISTER_INPUT:
                        // timeout before next targetAnonymitySet adjustment
                        long timeToWaitAnonymitySetAdjust = round.getTimeoutAdjustAnonymitySet()*1000 - elapsedTime;
                        // timeout before accepting liquidities
                        if (!round.isAcceptLiquidities()) {
                            long timeToWaitAcceptLiquidities = computeTimeToWaitAcceptLiquidities(elapsedTime);
                            timeToWait = Math.min(timeToWaitAnonymitySetAdjust, timeToWaitAcceptLiquidities);
                        }
                        else {
                            timeToWait = timeToWaitAnonymitySetAdjust;
                        }
                        break;

                    case REGISTER_OUTPUT:
                        timeToWait = whirlpoolServerConfig.getRegisterOutput().getTimeout() * 1000 - elapsedTime;
                        break;

                    case SIGNING:
                        timeToWait = whirlpoolServerConfig.getSigning().getTimeout() * 1000 - elapsedTime;
                        break;

                    default:
                        // should never use this default value
                        timeToWait = 30000;
                        break;
                }
                return timeToWait;
            }

            @Override
            public void onTimeout(TimeoutWatcher timeoutWatcher) {
                switch(round.getRoundStatus()) {
                    case REGISTER_INPUT:
                        long elapsedTime = timeoutWatcher.computeElapsedTime();
                        if (!round.isAcceptLiquidities() && computeTimeToWaitAcceptLiquidities(elapsedTime) <= 0) {
                            // accept liquidities
                            if (log.isDebugEnabled()) {
                                log.debug("accepting liquidities now (liquidityTimeout elapsed)");
                            }
                            round.setAcceptLiquidities(true);
                            addLiquidities(round);
                        }
                        else {
                            // adjust targetAnonymitySet
                            adjustTargetAnonymitySet(round, timeoutWatcher);
                        }
                        break;

                    case REGISTER_OUTPUT:
                        roundService.goRevealOutputOrBlame(round.getRoundId());
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

    // REGISTER_INPUTS

    private void adjustTargetAnonymitySet(Round round, TimeoutWatcher timeoutWatcher) {
        // no input registered yet => nothing to do
        if (round.getNbInputs() == 0) {
            return;
        }

        // round is ready => nothing to do
        if (round.getNbInputs() >= round.getTargetAnonymitySet() || round.getMinAnonymitySet() >= round.getTargetAnonymitySet()) {
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
        // first input registered => instanciate roundWatcher
        if (round.getNbInputs() == 1) {
            String roundId = round.getRoundId();
            TimeoutWatcher roundLimitsWatcher = computeRoundLimitsWatcher(round);
            this.roundWatchers.put(roundId, roundLimitsWatcher);
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
        roundService.goFail(round, RoundResult.FAIL_REGISTER_OUTPUTS);
    }

    public void blameForSigningAndResetRound(Round round) {
        log.info(" • SIGNING time over (round failed, blaming users who didn't sign...)");
        String roundId = round.getRoundId();

        // blame users who didn't sign
        Set<RegisteredInput> registeredInputsToBlame = round.getInputs().parallelStream().filter(input -> round.getSignatureByUsername(input.getUsername()) == null).collect(Collectors.toSet());
        registeredInputsToBlame.forEach(registeredInputToBlame -> blameService.blame(registeredInputToBlame, BlameReason.NO_SIGNING, roundId));

        // reset round
        roundService.goFail(round, RoundResult.FAIL_SIGNING);
    }

    public void __simulateElapsedTime(Round round, long elapsedTimeSeconds) {
        String roundId = round.getRoundId();
        log.info("__simulateElapsedTime for roundId="+roundId);
        TimeoutWatcher roundLimitsWatcher = roundWatchers.get(roundId);
        roundLimitsWatcher.__simulateElapsedTime(elapsedTimeSeconds);
    }
}
