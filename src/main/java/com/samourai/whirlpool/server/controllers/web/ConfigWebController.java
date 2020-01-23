package com.samourai.whirlpool.server.controllers.web;

import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.controllers.web.beans.WhirlpoolDashboardTemplateModel;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class ConfigWebController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String ENDPOINT = "/status/config";

  private WhirlpoolServerConfig serverConfig;

  @Autowired
  public ConfigWebController(WhirlpoolServerConfig serverConfig) {
    this.serverConfig = serverConfig;
  }

  @RequestMapping(value = ENDPOINT, method = RequestMethod.GET)
  public String status(Model model) {
    new WhirlpoolDashboardTemplateModel(serverConfig).apply(model);
    model.addAttribute("configInfo", serverConfig.getConfigInfo());
    return "config";
  }
}
