package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.rest.RestErrorResponse;
import com.samourai.whirlpool.server.exceptions.NotifiableException;
import org.springframework.http.ResponseEntity;

public abstract class AbstractRestController {
  public AbstractRestController() {}

  protected ResponseEntity<RestErrorResponse> handleException(Exception e) {
    String message =
        NotifiableException.class.isAssignableFrom(e.getClass()) ? e.getMessage() : "Server error";
    RestErrorResponse restErrorResponse = new RestErrorResponse(message);
    return ResponseEntity.badRequest().body(restErrorResponse);
  }
}
