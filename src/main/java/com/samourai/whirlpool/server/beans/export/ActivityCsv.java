package com.samourai.whirlpool.server.beans.export;

import com.opencsv.bean.CsvBindByPosition;
import com.samourai.whirlpool.server.beans.Activity;
import java.sql.Timestamp;

public class ActivityCsv {

  public static final String[] HEADERS =
      new String[] {
        "date",
        "activity",
        "poolId",
        "utxo",
        "value",
        "liquidity",
        "client",
        "userHash",
        "username",
        "ip"
      };

  @CsvBindByPosition(position = 0)
  private Timestamp date;

  @CsvBindByPosition(position = 1)
  private Activity activity;

  @CsvBindByPosition(position = 2)
  private String poolId;

  @CsvBindByPosition(position = 3)
  private String utxo;

  @CsvBindByPosition(position = 4)
  private long value;

  @CsvBindByPosition(position = 5)
  private int confirmations;

  @CsvBindByPosition(position = 6)
  private boolean liquidity;

  @CsvBindByPosition(position = 7)
  private String username;

  @CsvBindByPosition(position = 8)
  private String userHash;

  @CsvBindByPosition(position = 9)
  private String ip;

  @CsvBindByPosition(position = 10)
  private String headers;

  public ActivityCsv(
      Activity activity,
      String poolId,
      String utxo,
      long value,
      int confirmations,
      boolean liquidity,
      String username,
      String userHash,
      String ip,
      String headers) {
    this.date = new Timestamp(System.currentTimeMillis());
    this.activity = activity;
    this.poolId = poolId;
    this.utxo = utxo;
    this.value = value;
    this.confirmations = confirmations;
    this.liquidity = liquidity;
    this.username = username;
    this.userHash = userHash;
    this.ip = ip;
    this.headers = headers;
  }

  public Timestamp getDate() {
    return date;
  }

  public Activity getActivity() {
    return activity;
  }

  public String getPoolId() {
    return poolId;
  }

  public String getUtxo() {
    return utxo;
  }

  public long getValue() {
    return value;
  }

  public int getConfirmations() {
    return confirmations;
  }

  public boolean isLiquidity() {
    return liquidity;
  }

  public String getUsername() {
    return username;
  }

  public String getUserHash() {
    return userHash;
  }

  public String getIp() {
    return ip;
  }

  public String getHeaders() {
    return headers;
  }
}
