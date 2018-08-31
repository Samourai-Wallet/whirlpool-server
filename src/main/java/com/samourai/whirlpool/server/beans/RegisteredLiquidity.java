package com.samourai.whirlpool.server.beans;

public class RegisteredLiquidity {
    private RegisteredInput registeredInput;

    public RegisteredLiquidity(RegisteredInput registeredInput) {
        this.registeredInput = registeredInput;
    }

    public RegisteredInput getRegisteredInput() {
        return registeredInput;
    }
}
