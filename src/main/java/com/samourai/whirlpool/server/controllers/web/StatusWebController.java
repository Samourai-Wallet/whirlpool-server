package com.samourai.whirlpool.server.controllers.web;

import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;
import com.samourai.whirlpool.server.beans.LiquidityPool;
import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.services.RoundLimitsManager;
import com.samourai.whirlpool.server.services.RoundService;
import org.bouncycastle.util.Times;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.invoke.MethodHandles;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class StatusWebController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String ENDPOINT = "/status";

  private RoundService roundService;
  private RoundLimitsManager roundLimitsManager;

  @Autowired
  public StatusWebController(RoundService roundService, RoundLimitsManager roundLimitsManager) {
    this.roundService = roundService;
    this.roundLimitsManager = roundLimitsManager;
  }

  @RequestMapping(value = ENDPOINT, method = RequestMethod.GET)
  public String status(Model model) throws Exception {
    Round round = roundService.__getCurrentRound();
    model.addAttribute("roundId", round.getRoundId());
    model.addAttribute("roundStatus", round.getRoundStatus());
    model.addAttribute("targetAnonymitySet", round.getTargetAnonymitySet());
    model.addAttribute("maxAnonymitySet", round.getMaxAnonymitySet());
    model.addAttribute("minAnonymitySet", round.getMinAnonymitySet());
    model.addAttribute("nbInputs", round.getNbInputs());
    model.addAttribute("nbInputsMustMix", round.getNbInputsMustMix());
    model.addAttribute("nbInputsLiquidities", round.getNbInputsLiquidities());

    Long currentStepElapsedTime = toSeconds(this.roundLimitsManager.getLimitsWatcherElapsedTime(round));
    Long currentStepRemainingTime = toSeconds(this.roundLimitsManager.getLimitsWatcherTimeToWait(round));
    Double currentStepProgress = currentStepElapsedTime != null && currentStepRemainingTime != null ? Math.ceil(Double.valueOf(currentStepElapsedTime) / (currentStepElapsedTime+currentStepRemainingTime) * 100) : null;
    model.addAttribute("currentStepElapsedTime", currentStepElapsedTime);
    model.addAttribute("currentStepRemainingTime", currentStepRemainingTime);
    model.addAttribute("currentStepProgress", currentStepProgress);

    LiquidityPool liquidityPool = roundLimitsManager.getLiquidityPool(round);
    model.addAttribute("nbLiquiditiesAvailable", liquidityPool.getNbLiquidities());

    Map<RoundStatus, Timestamp> timeStatus = round.getTimeStatus();
    List<StatusStep> steps = new ArrayList<>();
    steps.add(new StatusStep(!timeStatus.isEmpty(), timeStatus.isEmpty(), "START_ROUND", null));
    steps.add(computeStep(RoundStatus.REGISTER_INPUT, timeStatus));
    steps.add(computeStep(RoundStatus.REGISTER_OUTPUT, timeStatus));
    if (timeStatus.containsKey(RoundStatus.REVEAL_OUTPUT_OR_BLAME)) {
      steps.add(computeStep(RoundStatus.REVEAL_OUTPUT_OR_BLAME, timeStatus));
      steps.add(computeStep(RoundStatus.FAIL, timeStatus));
    }
    else {
      steps.add(computeStep(RoundStatus.SIGNING, timeStatus));
      if (timeStatus.containsKey(RoundStatus.FAIL)) {
        steps.add(computeStep(RoundStatus.FAIL, timeStatus));
      }
      else {
        steps.add(computeStep(RoundStatus.SUCCESS, timeStatus));
      }
    }
    model.addAttribute("steps", steps);

    List<StatusEvent> events = new ArrayList<>();
    events.add(new StatusEvent(round.getTimeStarted(), "Round started", null));
    timeStatus.forEach(((roundStatus, timestamp) -> events.add(new StatusEvent(timestamp, roundStatus.toString(), null))));
    model.addAttribute("events", events);
    return "status";
  }

  private StatusStep computeStep(RoundStatus roundStatus, Map<RoundStatus, Timestamp> timeStatus) {
    boolean isActive = (!timeStatus.isEmpty() && new ArrayList<>(timeStatus.keySet()).indexOf(roundStatus) == (timeStatus.size()-1));
    boolean isDone = !isActive && timeStatus.containsKey(roundStatus);
    return new StatusStep(isDone, isActive, roundStatus.toString(), null);
  }

  private Long toSeconds(Long milliseconds) {
    if (milliseconds == null) {
      return null;
    }
    return milliseconds/1000;
  }

}