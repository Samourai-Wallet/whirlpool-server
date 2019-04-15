package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.server.persistence.to.shared.EntityCreatedTO;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity(name = "tx0Whitelist")
@Table(uniqueConstraints = {@UniqueConstraint(columnNames = {"txid"})})
public class Tx0WhitelistTO extends EntityCreatedTO {
  private String txid;

  public Tx0WhitelistTO() {}

  public Tx0WhitelistTO(String txid) {
    this.txid = txid;
  }

  public String getTxid() {
    return txid;
  }

  public void setTxid(String txid) {
    this.txid = txid;
  }
}
