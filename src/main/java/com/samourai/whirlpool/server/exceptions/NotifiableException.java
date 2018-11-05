package com.samourai.whirlpool.server.exceptions;

public class NotifiableException extends Exception {
  public NotifiableException(String message) {
    super(message);
  }

  public static String computeNotifiableMessage(Exception e) {
    String message =
        NotifiableException.class.isAssignableFrom(e.getClass()) ? e.getMessage() : "Server error";
    return message;
  }
}
