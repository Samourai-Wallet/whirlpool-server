package com.samourai.whirlpool.server.persistence.to;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class EntityTO {
    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Long id;

    private long timestamp;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EntityTO() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}
