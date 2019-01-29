package com.samourai.whirlpool.server.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class NotifiableException extends Exception {
  private static final Logger log = LoggerFactory.getLogger(NotifiableException.class);
  private static final HttpStatus STATUS_DEFAULT = HttpStatus.INTERNAL_SERVER_ERROR;

  private HttpStatus httpStatus;

  public NotifiableException(String message) {
    this(message, STATUS_DEFAULT);
  }

  public NotifiableException(HttpStatus status) {
    this(status.getReasonPhrase(), status);
  }

  public NotifiableException(String message, HttpStatus httpStatus) {
    super(message);
    this.httpStatus = httpStatus;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public static NotifiableException computeNotifiableException(Exception e) {
    if (NotifiableException.class.isAssignableFrom(e.getClass())) {
      return (NotifiableException) e;
    }
    log.warn("Exception obfuscated to user", e);
    return new NotifiableException("Error");
  }
}
