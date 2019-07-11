package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.server.persistence.to.shared.EntityCreatedTO;
import java.sql.Timestamp;
import javax.persistence.Entity;

@Entity(name = "ban")
public class BanTO extends EntityCreatedTO {
  private String identifier;

  private Timestamp expiration;

  private String message;

  private String notes;

  public BanTO() {}

  public BanTO(String identifier, Timestamp expiration, String message, String notes) {
    super();
    this.identifier = identifier;
    this.expiration = expiration;
    this.message = message;
    this.notes = notes;
  }

  public String getIdentifier() {
    return identifier;
  }

  public Timestamp getExpiration() {
    return expiration;
  }

  public String getMessage() {
    return message;
  }

  public String getNotes() {
    return notes;
  }

  @Override
  public String toString() {
    return "identifier="
        + identifier
        + ", expiration="
        + (expiration != null ? expiration : "null")
        + ", message="
        + (message != null ? message : "null")
        + ", notes="
        + (notes != null ? notes : "null")
        + ", created="
        + (getCreated() != null ? getCreated() : "");
  }
}
