package com.samourai.whirlpool.server.persistence.to;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.FailReason;
import com.samourai.whirlpool.server.beans.Mix;
import javax.persistence.*;

@Entity(name = "mix")
public class MixTO extends EntityTO {
  private String poolId;

  @Column(unique = true)
  private String mixId;

  private long denomination;

  private int anonymitySet;
  private int nbMustMix;
  private int nbLiquidities;

  @Enumerated(EnumType.STRING)
  private MixStatus mixStatus;

  @Enumerated(EnumType.STRING)
  private FailReason failReason;

  @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "mix")
  private MixLogTO mixLog;

  public MixTO() {}

  public void update(Mix mix) {
    this.poolId = mix.getPool().getPoolId();
    this.mixId = mix.getMixId();
    this.denomination = mix.getPool().getDenomination();
    this.anonymitySet = mix.getNbInputs();
    this.nbMustMix = mix.getNbInputsMustMix();
    this.nbLiquidities = mix.getNbInputsLiquidities();
    this.mixStatus = mix.getMixStatus();
    this.failReason = mix.getFailReason();

    if (this.mixLog == null) {
      this.mixLog = new MixLogTO();
    }
    this.mixLog.update(mix, this);
  }

  public String getPoolId() {
    return poolId;
  }

  public String getMixId() {
    return mixId;
  }

  public long getDenomination() {
    return denomination;
  }

  public int getAnonymitySet() {
    return anonymitySet;
  }

  public int getNbMustMix() {
    return nbMustMix;
  }

  public int getNbLiquidities() {
    return nbLiquidities;
  }

  public MixStatus getMixStatus() {
    return mixStatus;
  }

  public FailReason getFailReason() {
    return failReason;
  }

  public MixLogTO getMixLog() {
    return mixLog;
  }
}
