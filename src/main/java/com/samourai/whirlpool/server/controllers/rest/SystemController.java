package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
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
  public static final String ENDPOINT_HEALTH = WhirlpoolEndpoint.REST_PREFIX + "system/health";

  private HealthService healthService;

  @Autowired
  public SystemController(HealthService healthService) {
    this.healthService = healthService;
  }

  @RequestMapping(value = ENDPOINT_HEALTH, method = RequestMethod.GET)
  public HealthResponse health() {
    String lastError = healthService.getLastError();
    if (lastError != null) {
      return HealthResponse.error(lastError);
    }
    return HealthResponse.ok();
  }
}
