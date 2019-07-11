package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.server.persistence.to.shared.EntityCreatedTO;
import java.sql.Timestamp;
import javax.persistence.Entity;

@Entity(name = "ban")
public class BanTO extends EntityCreatedTO {
  private String identifier;

  private Timestamp expiration;

  private String response;

  private String notes;

  public BanTO() {}

  public BanTO(String identifier, Timestamp expiration, String response, String notes) {
    super();
    this.identifier = identifier;
    this.expiration = expiration;
    this.response = response;
    this.notes = notes;
  }

  public String getIdentifier() {
    return identifier;
  }

  public Timestamp getExpiration() {
    return expiration;
  }

  /*public String getResponse() {
    return response;
  }*/

  public String getNotes() {
    return notes;
  }

  public String computeBanMessage() {
    if (response == null) {
      response = "Contact us.";
    }
    String banMessage = "Banned from service. " + response;
    return banMessage;
  }

  @Override
  public String toString() {
    return "identifier="
        + identifier
        + ", expiration="
        + (expiration != null ? expiration : "null")
        + ", response="
        + (response != null ? response : "null")
        + ", notes="
        + (notes != null ? notes : "null")
        + ", created="
        + (getCreated() != null ? getCreated() : "");
  }
}
