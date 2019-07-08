package com.samourai.whirlpool.server.persistence.to.shared;

import java.sql.Timestamp;
import javax.persistence.MappedSuperclass;
import org.hibernate.annotations.UpdateTimestamp;

@MappedSuperclass
public abstract class EntityCreatedUpdatedTO extends EntityCreatedTO {
  public static final String UPDATED = "updated";

  @UpdateTimestamp private Timestamp updated;

  public EntityCreatedUpdatedTO() {}

  public Timestamp getUpdated() {
    return updated;
  }
}
