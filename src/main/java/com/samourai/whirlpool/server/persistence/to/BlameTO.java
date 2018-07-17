package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.BlameReason;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Entity(name = "blame")
public class BlameTO extends EntityTO {
    // TODO private RegisteredInput registeredInput;

    @Enumerated(EnumType.STRING)
    private BlameReason blameReason;

    private String roundId;

    public BlameTO() {
    }

    public BlameTO(RegisteredInput registeredInput, BlameReason blameReason, String roundId) {
        super();
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
