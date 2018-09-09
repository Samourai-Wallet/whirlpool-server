package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.rest.RestErrorResponse;
import org.springframework.http.ResponseEntity;

public abstract class AbstractRestController {
  public AbstractRestController() {
  }

  protected ResponseEntity<RestErrorResponse> handleException(Exception e){
    RestErrorResponse restErrorResponse = new RestErrorResponse();
    restErrorResponse.message = e.getMessage();
    return ResponseEntity.badRequest().body(restErrorResponse);
  }

}