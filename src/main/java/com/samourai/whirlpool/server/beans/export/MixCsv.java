package com.samourai.whirlpool.server.beans.export;

import com.opencsv.bean.CsvBindByPosition;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.beans.FailReason;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import java.sql.Timestamp;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

public class MixCsv {

  public static final String[] HEADERS =
      new String[] {
        "id",
        "created",
        "updated",
        "poolId",
        "mixId",
        "denomination",
        "anonymitySet",
        "nbMustMix",
        "nbLiquidities",
        "amountIn",
        "amountOut",
        "feesAmount",
        "feesPrice",
        "mixStatus",
        "failReason",
        "failInfo",
        "txid",
        "rawTx"
      };

  @CsvBindByPosition(position = 0)
  private Long id;

  @CsvBindByPosition(position = 1)
  private Timestamp created;

  @CsvBindByPosition(position = 2)
  private Timestamp updated;

  //

  @CsvBindByPosition(position = 3)
  private String poolId;

  @CsvBindByPosition(position = 4)
  private String mixId;

  @CsvBindByPosition(position = 5)
  private long denomination;

  @CsvBindByPosition(position = 6)
  private int anonymitySet;

  @CsvBindByPosition(position = 7)
  private int nbMustMix;

  @CsvBindByPosition(position = 8)
  private int nbLiquidities;

  @CsvBindByPosition(position = 9)
  private long amountIn;

  @CsvBindByPosition(position = 10)
  private long amountOut;

  @CsvBindByPosition(position = 11)
  private Long feesAmount;

  @CsvBindByPosition(position = 12)
  private Long feesPrice;

  @CsvBindByPosition(position = 13)
  private int mixDuration;

  @CsvBindByPosition(position = 14)
  @Enumerated(EnumType.STRING)
  private MixStatus mixStatus;

  @CsvBindByPosition(position = 15)
  @Enumerated(EnumType.STRING)
  private FailReason failReason;

  @CsvBindByPosition(position = 16)
  private String failInfo;

  //

  @CsvBindByPosition(position = 17)
  private String txid;

  @CsvBindByPosition(position = 18)
  private String rawTx;

  public MixCsv(MixTO to) {
    this.id = to.getId();
    this.created = to.getCreated();
    // us current date for 'updated', as the TO was just saved and may not be up-to-date
    this.updated = new Timestamp(System.currentTimeMillis());

    this.poolId = to.getPoolId();
    this.mixId = to.getMixId();
    this.denomination = to.getDenomination();
    this.anonymitySet = to.getAnonymitySet();
    this.nbMustMix = to.getNbMustMix();
    this.nbLiquidities = to.getNbLiquidities();
    this.amountIn = to.getAmountIn();
    this.amountOut = to.getAmountOut();
    this.feesAmount = to.getFeesAmount();
    this.feesPrice = to.getFeesPrice();
    this.mixDuration = to.getMixDuration();
    this.mixStatus = to.getMixStatus();
    this.failReason = to.getFailReason();
    this.failInfo = to.getFailInfo();

    if (to.getMixLog() != null) {
      this.txid = to.getMixLog().getTxid();
      this.rawTx = to.getMixLog().getRawTx();
    }
  }

  public Long getId() {
    return id;
  }

  public Timestamp getCreated() {
    return created;
  }

  public Timestamp getUpdated() {
    return updated;
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

  public long getAmountIn() {
    return amountIn;
  }

  public long getAmountOut() {
    return amountOut;
  }

  public Long getFeesAmount() {
    return feesAmount;
  }

  public Long getFeesPrice() {
    return feesPrice;
  }

  public int getMixDuration() {
    return mixDuration;
  }

  public MixStatus getMixStatus() {
    return mixStatus;
  }

  public FailReason getFailReason() {
    return failReason;
  }

  public String getFailInfo() {
    return failInfo;
  }

  public String getTxid() {
    return txid;
  }

  public String getRawTx() {
    return rawTx;
  }
}
