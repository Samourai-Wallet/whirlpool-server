package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.server.beans.RevocationType;
import java.sql.Timestamp;
import javax.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity(name = "revocation")
@Table(uniqueConstraints = {@UniqueConstraint(columnNames = {"revocationType", "value"})})
public class RevocationTO {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.ORDINAL)
  private RevocationType revocationType;

  private String value;

  @CreationTimestamp private Timestamp created;

  public RevocationTO() {}

  public RevocationTO(RevocationType revocationType, String value) {
    this.revocationType = revocationType;
    this.value = value;
  }

  public Long getId() {
    return id;
  }

  public RevocationType getRevocationType() {
    return revocationType;
  }

  public String getValue() {
    return value;
  }
}
