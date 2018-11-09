package com.samourai.whirlpool.server.controllers.web;

import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;

@RestController
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {
  private static final String ENDPOINT = "/error";

  @Autowired private ErrorAttributes errorAttributes;

  @RequestMapping(value = ENDPOINT)
  public ModelAndView errorHtml(WebRequest webRequest, HttpServletResponse response, Model model) {
    String errorMessage = getErrorMessage(webRequest, response.getStatus());
    model.addAttribute("errorMessage", errorMessage);
    return new ModelAndView("error.html");
  }

  private String getErrorMessage(WebRequest webRequest, int status) {
    Throwable cause = errorAttributes.getError(webRequest);
    return (cause != null ? cause.getMessage() : String.valueOf(status));
  }

  @Override
  public String getErrorPath() {
    return ENDPOINT;
  }
}
