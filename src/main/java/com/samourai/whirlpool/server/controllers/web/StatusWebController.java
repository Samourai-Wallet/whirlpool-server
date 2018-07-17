package com.samourai.whirlpool.server.controllers.web;

import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;
import com.samourai.whirlpool.server.beans.LiquidityPool;
import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.services.RoundLimitsService;
import com.samourai.whirlpool.server.services.RoundService;
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
  private static final String STATUS_START_ROUND = "START_ROUND";

  private RoundService roundService;
  private RoundLimitsService roundLimitsService;

  @Autowired
  public StatusWebController(RoundService roundService, RoundLimitsService roundLimitsService) {
    this.roundService = roundService;
    this.roundLimitsService = roundLimitsService;
  }

  @RequestMapping(value = ENDPOINT, method = RequestMethod.GET)
  public String status(Model model) throws Exception {
    Round round = roundService.__getCurrentRound();
    model.addAttribute("roundId", round.getRoundId());
    model.addAttribute("roundStatus", round.getNbInputsMustMix() > 0 ? round.getRoundStatus() : STATUS_START_ROUND);
    model.addAttribute("targetAnonymitySet", round.getTargetAnonymitySet());
    model.addAttribute("maxAnonymitySet", round.getMaxAnonymitySet());
    model.addAttribute("minAnonymitySet", round.getMinAnonymitySet());
    model.addAttribute("nbInputs", round.getNbInputs());
    model.addAttribute("nbInputsMustMix", round.getNbInputsMustMix());
    model.addAttribute("nbInputsLiquidities", round.getNbInputsLiquidities());

    Long currentStepElapsedTime = toSeconds(this.roundLimitsService.getLimitsWatcherElapsedTime(round));
    Long currentStepRemainingTime = toSeconds(this.roundLimitsService.getLimitsWatcherTimeToWait(round));
    Double currentStepProgress = currentStepElapsedTime != null && currentStepRemainingTime != null ? Math.ceil(Double.valueOf(currentStepElapsedTime) / (currentStepElapsedTime+currentStepRemainingTime) * 100) : null;
    model.addAttribute("currentStepProgress", currentStepProgress);

    String currentStepProgressLabel = computeCurrentStepProgressLabel(round.getRoundStatus(), currentStepElapsedTime, currentStepRemainingTime);
    model.addAttribute("currentStepProgressLabel", currentStepProgressLabel);

    LiquidityPool liquidityPool = roundLimitsService.getLiquidityPool(round);
    model.addAttribute("nbLiquiditiesAvailable", liquidityPool.getNbLiquidities());

    Map<RoundStatus, Timestamp> timeStatus = round.getTimeStatus();
    List<StatusStep> steps = new ArrayList<>();
    steps.add(new StatusStep(!timeStatus.isEmpty(), timeStatus.isEmpty(), STATUS_START_ROUND, null));
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
    events.add(new StatusEvent(round.getTimeStarted(), STATUS_START_ROUND, null));
    timeStatus.forEach(((roundStatus, timestamp) -> events.add(new StatusEvent(timestamp, roundStatus.toString(), null))));
    model.addAttribute("events", events);
    return "status";
  }

  private StatusStep computeStep(RoundStatus roundStatus, Map<RoundStatus, Timestamp> timeStatus) {
    boolean isActive = (!timeStatus.isEmpty() && new ArrayList<>(timeStatus.keySet()).indexOf(roundStatus) == (timeStatus.size()-1));
    boolean isDone = !isActive && timeStatus.containsKey(roundStatus);
    return new StatusStep(isDone, isActive, roundStatus.toString(), null);
  }

  private String computeCurrentStepProgressLabel(RoundStatus roundStatus, Long currentStepElapsedTime, Long currentStepRemainingTime) {
      String progressLabel = null;

      if (currentStepElapsedTime != null && currentStepRemainingTime != null) {
          progressLabel = currentStepElapsedTime + "s elapsed, " + currentStepRemainingTime + "s remaining ";
          switch (roundStatus) {
              case REGISTER_INPUT:
                  progressLabel += "before anonymitySet adjustment";
                  break;
              case REGISTER_OUTPUT:
                  progressLabel += "to register outputs";
                  break;
              case SIGNING:
                  progressLabel += "to sign";
                  break;
              case REVEAL_OUTPUT_OR_BLAME:
                  progressLabel += "to reveal outputs";
                  break;
          }
      }
      return progressLabel;
  }

  private Long toSeconds(Long milliseconds) {
    if (milliseconds == null) {
      return null;
    }
    return milliseconds/1000;
  }

}