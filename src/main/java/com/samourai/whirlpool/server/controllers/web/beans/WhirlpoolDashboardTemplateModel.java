package com.samourai.whirlpool.server.controllers.web.beans;

import com.samourai.javaserver.web.models.DashboardTemplateModel;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;

public class WhirlpoolDashboardTemplateModel extends DashboardTemplateModel {
  public WhirlpoolDashboardTemplateModel(WhirlpoolServerConfig serverConfig) {
    super(serverConfig.getName(), serverConfig.getName());
  }
}
