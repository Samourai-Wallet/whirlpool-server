package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.javaserver.rest.AbstractRestExceptionHandler;
import com.samourai.whirlpool.protocol.rest.RestErrorResponse;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class RestExceptionHandler extends AbstractRestExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  protected Object handleError(com.samourai.javaserver.exceptions.NotifiableException e) {
    log.warn("RestException -> " + e.getMessage());
    return new RestErrorResponse(e.getMessage());
  }
}
