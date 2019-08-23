package com.samourai.whirlpool.server.exceptions;

public class BannedInputException extends IllegalInputException {

  public BannedInputException(String message) {
    super(message);
  }
}
