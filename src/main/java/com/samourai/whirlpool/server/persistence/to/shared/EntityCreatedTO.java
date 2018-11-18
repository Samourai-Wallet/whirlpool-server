package com.samourai.whirlpool.server.persistence.to.shared;

import java.sql.Timestamp;
import javax.persistence.MappedSuperclass;
import org.hibernate.annotations.CreationTimestamp;

@MappedSuperclass
public abstract class EntityCreatedTO extends EntityTO {
  public static final String CREATED = "created";

  @CreationTimestamp private Timestamp created;

  public EntityCreatedTO() {}

  public Timestamp getCreated() {
    return created;
  }
}
