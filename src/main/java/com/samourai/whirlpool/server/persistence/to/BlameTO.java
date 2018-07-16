package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.BlameReason;

import javax.persistence.Entity;

@Entity
public class BlameTO extends EntityTO {
    // TODO private RegisteredInput registeredInput;
    private BlameReason blameReason;
    private String roundId;

    public BlameTO(RegisteredInput registeredInput, BlameReason blameReason, String roundId) {
        this.blameReason = blameReason;
        this.roundId = roundId;
    }

    public BlameReason getBlameReason() {
        return blameReason;
    }

    public String getRoundId() {
        return roundId;
    }
}
