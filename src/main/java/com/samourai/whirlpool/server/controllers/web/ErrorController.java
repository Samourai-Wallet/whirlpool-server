package com.samourai.whirlpool.server.controllers.web;

import com.samourai.javaserver.web.controllers.AbstractErrorWebController;
import com.samourai.javaserver.web.models.ErrorTemplateModel;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.ExportService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;

@RestController
public class ErrorController extends AbstractErrorWebController {
  private static final String ENDPOINT = "/error";

  private WhirlpoolServerConfig serverConfig;
  private ExportService exportService;

  @Autowired
  public ErrorController(WhirlpoolServerConfig serverConfig, ExportService exportService) {
    this.serverConfig = serverConfig;
    this.exportService = exportService;
  }

  @RequestMapping(value = ENDPOINT)
  public ModelAndView errorHtml(
      WebRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response,
      Model model) {
    ErrorTemplateModel errorTemplateModel = new ErrorTemplateModel(serverConfig.getName());
    ModelAndView result = super.errorHtml(request, response, model, errorTemplateModel);

    String errorMsg = errorTemplateModel.errorMessage; // was set by super.errorHtml()

    // log activity
    ActivityCsv activityCsv =
        new ActivityCsv("WEB:ERROR", httpRequest.getRequestURI(), errorMsg, null, httpRequest);
    exportService.exportActivity(activityCsv);

    return result;
  }

  @Override
  public String getErrorPath() {
    return ENDPOINT;
  }
}
