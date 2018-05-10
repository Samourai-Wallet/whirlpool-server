package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.beans.*;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.exceptions.RoundException;
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
    private Map<String, RoundLimitsWatcher> roundWatchers;

    public RoundLimitsManager(RoundService roundService, BlameService blameService, WhirlpoolServerConfig whirlpoolServerConfig) {
        this.roundService = roundService;
        this.blameService = blameService;
        this.whirlpoolServerConfig = whirlpoolServerConfig;

        this.liquidityPools = new HashMap<>();
        this.roundWatchers = new HashMap<>();
    }

    private RoundLimitsWatcher getRoundLimitsWatcher(Round round) {
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

        // create roundWatcher
        RoundLimitsWatcher roundLimitsWatcher = new RoundLimitsWatcher(round, this);
        this.roundWatchers.put(roundId, roundLimitsWatcher);
    }

    public void unmanage(Round round) {
        String roundId = round.getRoundId();
        liquidityPools.remove(roundId);

        RoundLimitsWatcher roundLimitsWatcher = getRoundLimitsWatcher(round);
        if (roundLimitsWatcher != null) {
            roundLimitsWatcher.stop();
            roundWatchers.remove(roundId);
        }
    }

    public long computeRoundWatcherTimeToWait(long waitSince, Round round) {
        long elapsedTime = System.currentTimeMillis() - waitSince;

        long timeToWait;
        switch(round.getRoundStatus()) {
            case REGISTER_INPUT:
                // timeout before next targetMustMix adjustment
                timeToWait = round.getMustMixAdjustTimeout()*1000 - elapsedTime;
            break;

            case REGISTER_OUTPUT:
                timeToWait = whirlpoolServerConfig.getRegisterOutput().getTimeout() * 1000;
            break;

            case SIGNING:
                timeToWait = whirlpoolServerConfig.getSigning().getTimeout() * 1000;
                break;

            default:
                // should never use this default value
                timeToWait = 10000;
                break;
        }
        return timeToWait;
    }

    public void onRoundStatusChange(Round round) {
        RoundLimitsWatcher roundLimitsWatcher = getRoundLimitsWatcher(round);

        // reset timeout for new roundStatus
        roundLimitsWatcher.resetTimeout();
    }

    public void onRoundWatcherTimeout(Round round, RoundLimitsWatcher roundLimitsWatcher) {
        switch(round.getRoundStatus()) {
            case REGISTER_INPUT:
                adjustTargetMustMix(round, roundLimitsWatcher);
                break;

            case REGISTER_OUTPUT:
                roundService.goRevealOutputOrBlame(round.getRoundId());
                break;

            case SIGNING:
                blameForSigningAndResetRound(round);
                break;
        }
    }

    // REGISTER_INPUTS

    private void adjustTargetMustMix(Round round, RoundLimitsWatcher roundLimitsWatcher) {
        // no input registered yet => nothing to do
        if (round.getNbInputs() == 0) {
            return;
        }

        // round is ready => nothing to do
        if (round.getNbInputs() >= round.getTargetMustMix() || round.getMinMustMix() >= round.getTargetMustMix()) {
            return;
        }

        // adjust mustMix
        round.setTargetMustMix(round.getTargetMustMix() - 1);
        roundLimitsWatcher.resetTimeout();

        checkAddLiquidity(round);
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
        RoundLimitsWatcher roundLimitsWatcher = getRoundLimitsWatcher(round);

        // first input registered => reset timeout for next targetMustMix adjustment
        if (round.getNbInputs() == 1) {
            roundLimitsWatcher.resetTimeout();
        }

        // check if liquidity is needed
        checkAddLiquidity(round);
    }

    private void checkAddLiquidity(Round round) {
        if (round.getNbInputs() == round.getTargetMustMix()) {
            try {
                addLiquidities(round);
            } catch (Exception e) {
                log.error("addLiquiditiesFailed", e);
            }
        }
    }

    private void addLiquidities(Round round) throws RoundException {
        LiquidityPool liquidityPool = getLiquidityPool(round);
        int liquiditiesAdded = 0;
        int liquiditiesToAdd = round.computeLiquiditiesExpected();
        while (liquiditiesAdded < liquiditiesToAdd && liquidityPool.hasLiquidity()) {
            log.info("Registering liquidity "+(liquiditiesAdded+1)+"/"+liquiditiesToAdd);
            RegisteredLiquidity randomLiquidity = liquidityPool.peekRandomLiquidity();
            try {
                roundService.addLiquidity(round, randomLiquidity);
                liquiditiesAdded++;
            } catch (Exception e) {
                log.error("registerInput error when adding more liquidity", e);
                // ignore the error and continue with more liquidity
            }
        }

        int missingLiquidities = liquiditiesToAdd - liquiditiesAdded;
        if (missingLiquidities > 0) {
            log.warn("Not enough liquidities to start the round! "+missingLiquidities+" liquidities missing");
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
        RoundLimitsWatcher roundLimitsWatcher = roundWatchers.get(roundId);
        roundLimitsWatcher.__simulateElapsedTime(elapsedTimeSeconds);
    }
}
