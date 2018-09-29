package com.samourai.whirlpool.server.exceptions;


import com.samourai.whirlpool.server.beans.RegisteredInput;

public class QueueInputException extends Exception {
    private RegisteredInput registeredInput;
    private String poolId;

    public QueueInputException(String message, RegisteredInput registeredInput, String poolId) {
        super(message);
        this.registeredInput = registeredInput;
        this.poolId = poolId;
    }

    public RegisteredInput getRegisteredInput() {
        return registeredInput;
    }

    public String getPoolId() {
        return poolId;
    }
}
