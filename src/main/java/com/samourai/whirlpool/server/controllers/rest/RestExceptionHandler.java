package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.rest.RestErrorResponse;
import com.samourai.whirlpool.server.exceptions.NotifiableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(value = {Exception.class})
  protected ResponseEntity<Object> handleException(Exception e) {
    NotifiableException notifiable = NotifiableException.computeNotifiableException(e);
    RestErrorResponse restErrorResponse = new RestErrorResponse(notifiable.getMessage());
    return new ResponseEntity<>(restErrorResponse, notifiable.getHttpStatus());
  }
}
