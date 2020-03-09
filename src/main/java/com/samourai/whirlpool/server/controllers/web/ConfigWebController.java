package com.samourai.whirlpool.server.controllers.web;

import com.samourai.javaserver.utils.ServerUtils;
import com.samourai.javaserver.web.controllers.AbstractConfigWebController;
import com.samourai.javaserver.web.models.ConfigTemplateModel;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class ConfigWebController extends AbstractConfigWebController {
  public static final String ENDPOINT = "/status/config";

  private WhirlpoolServerConfig serverConfig;
  private ServerProperties serverProperties;

  @Autowired
  public ConfigWebController(
      WhirlpoolServerConfig serverConfig, ServerProperties serverProperties) {
    this.serverConfig = serverConfig;
    this.serverProperties = serverProperties;
  }

  @RequestMapping(value = ENDPOINT, method = RequestMethod.GET)
  public String config(Model model) {
    ConfigTemplateModel configTemplateModel =
        new ConfigTemplateModel(
            serverConfig.getName(), serverConfig.getName(), serverConfig.getConfigInfo());
    configTemplateModel.configInfo.put(
        "serverProperties", ServerUtils.getInstance().toJsonString(serverProperties));
    return super.config(model, configTemplateModel);
  }
}
