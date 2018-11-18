package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.server.persistence.to.shared.EntityCreatedTO;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity(name = "mixTxid")
@Table(uniqueConstraints = {@UniqueConstraint(columnNames = {"txid"})})
public class MixTxidTO extends EntityCreatedTO {
  private String txid;
  private long denomination;

  public MixTxidTO() {}

  public MixTxidTO(String txid, long denomination) {
    this.txid = txid;
    this.denomination = denomination;
  }

  public String getTxid() {
    return txid;
  }

  public void setTxid(String txid) {
    this.txid = txid;
  }

  public long getDenomination() {
    return denomination;
  }

  public void setDenomination(long denomination) {
    this.denomination = denomination;
  }
}
