package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.server.controllers.rest.beans.HealthResponse;
import com.samourai.whirlpool.server.services.HealthService;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemController extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private HealthService healthService;

  @Autowired
  public SystemController(HealthService healthService) {
    this.healthService = healthService;
  }

  @RequestMapping(value = "/rest/system/health", method = RequestMethod.GET)
  public HealthResponse health() {
    Exception lastError = healthService.getLastError();
    if (lastError != null) {
      return HealthResponse.error(lastError.getMessage());
    }
    return HealthResponse.ok();
  }
}
