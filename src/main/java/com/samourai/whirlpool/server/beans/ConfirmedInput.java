package com.samourai.whirlpool.server.beans;

public class ConfirmedInput {
  private RegisteredInput registeredInput;
  private byte[] blindedBordereau;
  private boolean offline;

  public ConfirmedInput(RegisteredInput registeredInput, byte[] blindedBordereau) {
    this.registeredInput = registeredInput;
    this.blindedBordereau = blindedBordereau;
    this.offline = false;
  }

  public RegisteredInput getRegisteredInput() {
    return registeredInput;
  }

  public byte[] getBlindedBordereau() {
    return blindedBordereau;
  }

  public void setOffline(boolean offline) {
    this.offline = offline;
  }

  public boolean isOffline() {
    return offline;
  }
}
