package com.samourai.whirlpool.server.beans;

public class ConfirmedInput {
  private RegisteredInput registeredInput;
  private String userHash;

  public ConfirmedInput(RegisteredInput registeredInput, String userHash) {
    this.registeredInput = registeredInput;
    this.userHash = userHash;
  }

  public RegisteredInput getRegisteredInput() {
    return registeredInput;
  }

  public String getUserHash() {
    return userHash;
  }

  @Override
  public String toString() {
    return "registeredInput=[" + registeredInput + "], userHash=" + userHash;
  }
}
