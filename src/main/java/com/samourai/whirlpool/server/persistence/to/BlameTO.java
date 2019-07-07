package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.server.beans.BlameReason;
import com.samourai.whirlpool.server.persistence.to.shared.EntityCreatedTO;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Entity(name = "blame")
public class BlameTO extends EntityCreatedTO {
  private String identifier;

  @Enumerated(EnumType.STRING)
  private BlameReason reason;

  private String mixId;

  private String ip;

  public BlameTO() {}

  public BlameTO(String identifier, BlameReason reason, String mixId, String ip) {
    super();
    this.identifier = identifier;
    this.reason = reason;
    this.mixId = mixId;
    this.ip = ip;
  }

  public String getIdentifier() {
    return identifier;
  }

  public BlameReason getReason() {
    return reason;
  }

  public String getMixId() {
    return mixId;
  }

  public String getIp() {
    return ip;
  }

  @Override
  public String toString() {
    return "identifier="
        + identifier
        + ", reason="
        + reason
        + ", mixId="
        + (mixId != null ? mixId : "null")
        + ", ip="
        + (ip != null ? ip : "null")
        + ", created="
        + (getCreated() != null ? getCreated() : "");
  }
}
