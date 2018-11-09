package com.samourai.whirlpool.server.controllers.web;

import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class LoginWebController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String ENDPOINT = "/status/login-form";
  public static final String PROCESS_ENDPOINT = "/status/doLogin";

  @Autowired
  public LoginWebController() {}

  @RequestMapping(value = ENDPOINT, method = RequestMethod.GET)
  public String login(Model model) {
    model.addAttribute("action", PROCESS_ENDPOINT);
    return "login";
  }
}
