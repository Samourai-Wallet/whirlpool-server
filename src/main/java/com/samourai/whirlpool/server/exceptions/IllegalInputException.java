package com.samourai.whirlpool.server.exceptions;

import com.samourai.javaserver.exceptions.NotifiableException;

public class IllegalInputException extends NotifiableException {

  public IllegalInputException(String message) {
    super(message);
  }
}
