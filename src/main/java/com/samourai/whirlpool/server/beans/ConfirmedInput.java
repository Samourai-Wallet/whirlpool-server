package com.samourai.whirlpool.server.beans;

public class ConfirmedInput {
  private RegisteredInput registeredInput;
  private byte[] blindedBordereau;

  public ConfirmedInput(RegisteredInput registeredInput, byte[] blindedBordereau) {
    this.registeredInput = registeredInput;
    this.blindedBordereau = blindedBordereau;
  }

  public RegisteredInput getRegisteredInput() {
    return registeredInput;
  }

  public byte[] getBlindedBordereau() {
    return blindedBordereau;
  }
}
