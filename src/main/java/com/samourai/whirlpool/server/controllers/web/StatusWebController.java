package com.samourai.whirlpool.server.controllers.web;

import com.samourai.whirlpool.protocol.v1.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.LiquidityPool;
import com.samourai.whirlpool.server.beans.Mix;
import com.samourai.whirlpool.server.services.MixLimitsService;
import com.samourai.whirlpool.server.services.MixService;
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
  private static final String STATUS_START_MIX = "START_MIX";

  private MixService mixService;
  private MixLimitsService mixLimitsService;

  @Autowired
  public StatusWebController(MixService mixService, MixLimitsService mixLimitsService) {
    this.mixService = mixService;
    this.mixLimitsService = mixLimitsService;
  }

  @RequestMapping(value = ENDPOINT, method = RequestMethod.GET)
  public String status(Model model) throws Exception {
    Mix mix = mixService.__getCurrentMix();
    model.addAttribute("mixId", mix.getMixId());
    model.addAttribute("mixStatus", mix.getNbInputsMustMix() > 0 ? mix.getMixStatus() : STATUS_START_MIX);
    model.addAttribute("targetAnonymitySet", mix.getTargetAnonymitySet());
    model.addAttribute("maxAnonymitySet", mix.getMaxAnonymitySet());
    model.addAttribute("minAnonymitySet", mix.getMinAnonymitySet());
    model.addAttribute("nbInputs", mix.getNbInputs());
    model.addAttribute("nbInputsMustMix", mix.getNbInputsMustMix());
    model.addAttribute("nbInputsLiquidities", mix.getNbInputsLiquidities());

    Long currentStepElapsedTime = toSeconds(this.mixLimitsService.getLimitsWatcherElapsedTime(mix));
    Long currentStepRemainingTime = toSeconds(this.mixLimitsService.getLimitsWatcherTimeToWait(mix));
    Double currentStepProgress = currentStepElapsedTime != null && currentStepRemainingTime != null ? Math.ceil(Double.valueOf(currentStepElapsedTime) / (currentStepElapsedTime+currentStepRemainingTime) * 100) : null;
    model.addAttribute("currentStepProgress", currentStepProgress);

    String currentStepProgressLabel = computeCurrentStepProgressLabel(mix.getMixStatus(), currentStepElapsedTime, currentStepRemainingTime);
    model.addAttribute("currentStepProgressLabel", currentStepProgressLabel);

    LiquidityPool liquidityPool = mixLimitsService.getLiquidityPool(mix);
    model.addAttribute("nbLiquiditiesAvailable", liquidityPool.getNbLiquidities());

    Map<MixStatus, Timestamp> timeStatus = mix.getTimeStatus();
    List<StatusStep> steps = new ArrayList<>();
    steps.add(new StatusStep(!timeStatus.isEmpty(), timeStatus.isEmpty(), STATUS_START_MIX, null));
    steps.add(computeStep(MixStatus.REGISTER_INPUT, timeStatus));
    steps.add(computeStep(MixStatus.REGISTER_OUTPUT, timeStatus));
    if (timeStatus.containsKey(MixStatus.REVEAL_OUTPUT)) {
      steps.add(computeStep(MixStatus.REVEAL_OUTPUT, timeStatus));
      steps.add(computeStep(MixStatus.FAIL, timeStatus));
    }
    else {
      steps.add(computeStep(MixStatus.SIGNING, timeStatus));
      if (timeStatus.containsKey(MixStatus.FAIL)) {
        steps.add(computeStep(MixStatus.FAIL, timeStatus));
      }
      else {
        steps.add(computeStep(MixStatus.SUCCESS, timeStatus));
      }
    }
    model.addAttribute("steps", steps);

    List<StatusEvent> events = new ArrayList<>();
    events.add(new StatusEvent(mix.getTimeStarted(), STATUS_START_MIX, null));
    timeStatus.forEach(((mixStatus, timestamp) -> events.add(new StatusEvent(timestamp, mixStatus.toString(), null))));
    model.addAttribute("events", events);
    return "status";
  }

  private StatusStep computeStep(MixStatus mixStatus, Map<MixStatus, Timestamp> timeStatus) {
    boolean isActive = (!timeStatus.isEmpty() && new ArrayList<>(timeStatus.keySet()).indexOf(mixStatus) == (timeStatus.size()-1));
    boolean isDone = !isActive && timeStatus.containsKey(mixStatus);
    return new StatusStep(isDone, isActive, mixStatus.toString(), null);
  }

  private String computeCurrentStepProgressLabel(MixStatus mixStatus, Long currentStepElapsedTime, Long currentStepRemainingTime) {
      String progressLabel = null;

      if (currentStepElapsedTime != null && currentStepRemainingTime != null) {
          progressLabel = currentStepElapsedTime + "s elapsed, " + currentStepRemainingTime + "s remaining ";
          switch (mixStatus) {
              case REGISTER_INPUT:
                  progressLabel += "before anonymitySet adjustment";
                  break;
              case REGISTER_OUTPUT:
                  progressLabel += "to register outputs";
                  break;
              case SIGNING:
                  progressLabel += "to sign";
                  break;
              case REVEAL_OUTPUT:
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