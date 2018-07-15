package com.samourai.whirlpool.server.controllers.web;

import com.samourai.whirlpool.server.beans.LiquidityPool;
import com.samourai.whirlpool.server.beans.Round;
import com.samourai.whirlpool.server.services.RoundLimitsManager;
import com.samourai.whirlpool.server.services.RoundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.invoke.MethodHandles;

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
    model.addAttribute("progressPercent", 50);

    LiquidityPool liquidityPool = roundLimitsManager.getLiquidityPool(round);
    model.addAttribute("nbLiquiditiesAvailable", liquidityPool.getNbLiquidities());
    return "status";
  }

}