package com.samourai.whirlpool.server.beans;

public class RegisteredLiquidity {
    private RegisteredInput registeredInput;
    private byte[] signedBordereau;

    public RegisteredLiquidity(RegisteredInput registeredInput, byte[] signedBordereau) {
        this.registeredInput = registeredInput;
        this.signedBordereau = signedBordereau;
    }

    public RegisteredInput getRegisteredInput() {
        return registeredInput;
    }

    public byte[] getSignedBordereau() {
        return signedBordereau;
    }
}
