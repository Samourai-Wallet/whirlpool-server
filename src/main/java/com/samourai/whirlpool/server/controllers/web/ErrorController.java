package com.samourai.whirlpool.server.controllers.web;

import com.samourai.javaserver.web.controllers.AbstractErrorWebController;
import com.samourai.javaserver.web.models.ErrorTemplateModel;
import com.samourai.whirlpool.server.utils.Utils;
import javax.servlet.http.HttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;

@RestController
public class ErrorController extends AbstractErrorWebController {
  private static final String ENDPOINT = "/error";

  @RequestMapping(value = ENDPOINT)
  public ModelAndView errorHtml(WebRequest webRequest, HttpServletResponse response, Model model) {
    return super.errorHtml(
        webRequest, response, model, new ErrorTemplateModel(Utils.WEB_PAGE_TITLE));
  }

  @Override
  public String getErrorPath() {
    return ENDPOINT;
  }
}
