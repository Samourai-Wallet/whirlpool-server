package com.samourai.whirlpool.server.controllers.web.beans;

import com.samourai.javaserver.web.models.DashboardTemplateModel;
import com.samourai.whirlpool.server.utils.Utils;

public class WhirlpoolDashboardTemplateModel extends DashboardTemplateModel {
  public WhirlpoolDashboardTemplateModel() {
    super(Utils.WEB_PAGE_TITLE);
  }
}
