package com.samourai.whirlpool.server.beans;

public class ConfirmedInput {
  private RegisteredInput registeredInput;
  private byte[] blindedBordereau;
  private String userHash;

  public ConfirmedInput(RegisteredInput registeredInput, byte[] blindedBordereau, String userHash) {
    this.registeredInput = registeredInput;
    this.blindedBordereau = blindedBordereau;
    this.userHash = userHash;
  }

  public RegisteredInput getRegisteredInput() {
    return registeredInput;
  }

  public byte[] getBlindedBordereau() {
    return blindedBordereau;
  }

  public String getUserHash() {
    return userHash;
  }

  @Override
  public String toString() {
    return "registeredInput=["+registeredInput+"], userHash="+userHash;
  }
}
