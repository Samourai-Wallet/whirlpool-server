package com.samourai.whirlpool.server.exceptions;

import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotifiableException extends Exception {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public NotifiableException(String message) {
    super(message);
  }

  public static String computeNotifiableMessage(Exception e) {
    if (!NotifiableException.class.isAssignableFrom(e.getClass())) {
      log.warn("Exception obfuscated to user", e);
      return "Server error";
    }
    return e.getMessage();
  }
}
