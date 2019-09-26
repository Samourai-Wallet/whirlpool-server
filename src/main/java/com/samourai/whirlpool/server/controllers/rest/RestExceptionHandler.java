package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.rest.RestErrorResponse;
import com.samourai.whirlpool.server.exceptions.NotifiableException;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @ExceptionHandler(value = {Exception.class})
  protected ResponseEntity<Object> handleException(Exception e) {
    NotifiableException notifiable = NotifiableException.computeNotifiableException(e);
    log.warn("RestException -> " + notifiable.getMessage());
    RestErrorResponse restErrorResponse = new RestErrorResponse(notifiable.getMessage());
    return new ResponseEntity<>(restErrorResponse, notifiable.getHttpStatus());
  }
}
