package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.beans.ConfirmedInput;
import com.samourai.whirlpool.server.persistence.to.shared.EntityCreatedUpdatedTO;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Entity(name = "blame")
public class BlameTO extends EntityCreatedUpdatedTO {
  // TODO private RegisteredInput registeredInput;

  @Enumerated(EnumType.STRING)
  private BlameReason blameReason;

  private String mixId;

  public BlameTO() {}

  public BlameTO(ConfirmedInput confirmedInput, BlameReason blameReason, String mixId) {
    super();
    this.blameReason = blameReason;
    this.mixId = mixId;
  }

  public BlameReason getBlameReason() {
    return blameReason;
  }

  public String getMixId() {
    return mixId;
  }
}
