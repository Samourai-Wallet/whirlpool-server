package com.samourai.whirlpool.server.to;

import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.BlameReason;

public class BlameTO {
    private RegisteredInput registeredInput;
    private BlameReason blameReason;
    private String roundId;

    public BlameTO(RegisteredInput registeredInput, BlameReason blameReason, String roundId) {
        this.registeredInput = registeredInput;
        this.blameReason = blameReason;
        this.roundId = roundId;
    }

    public RegisteredInput getRegisteredInput() {
        return registeredInput;
    }

    public BlameReason getBlameReason() {
        return blameReason;
    }

    public String getRoundId() {
        return roundId;
    }
}
