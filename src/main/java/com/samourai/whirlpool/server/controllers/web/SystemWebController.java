package com.samourai.whirlpool.server.controllers.web;

import com.samourai.javaserver.web.controllers.AbstractSystemWebController;
import com.samourai.javaserver.web.models.SystemTemplateModel;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class SystemWebController extends AbstractSystemWebController {
  public static final String ENDPOINT = "/status/system";

  private WhirlpoolServerConfig serverConfig;
  private SimpUserRegistry simpUserRegistry;

  @Autowired
  public SystemWebController(
      WhirlpoolServerConfig serverConfig, SimpUserRegistry simpUserRegistry) {
    this.serverConfig = serverConfig;
    this.simpUserRegistry = simpUserRegistry;
  }

  @RequestMapping(value = ENDPOINT, method = RequestMethod.GET)
  public String system(Model model) {
    Map<String, String> metrics = new HashMap<>();
    metrics.put("Active sessions", String.valueOf(simpUserRegistry.getUserCount()));
    return super.system(
        model, new SystemTemplateModel(serverConfig.getName(), serverConfig.getName(), metrics));
  }
}
