package com.samourai.whirlpool.server.beans.export;

import com.opencsv.bean.CsvBindByPosition;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.rpc.TxOutPoint;
import java.lang.invoke.MethodHandles;
import java.sql.Timestamp;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActivityCsv {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final String[] HEADERS =
      new String[] {"date", "activity", "poolId", "arg", "details", "ip", "clientDetails"};

  @CsvBindByPosition(position = 0)
  private Timestamp date;

  @CsvBindByPosition(position = 1)
  private String activity;

  @CsvBindByPosition(position = 2)
  private String poolId;

  @CsvBindByPosition(position = 3)
  private String arg;

  @CsvBindByPosition(position = 4)
  private String details;

  @CsvBindByPosition(position = 5)
  private String ip;

  @CsvBindByPosition(position = 6)
  private String clientDetails;

  public ActivityCsv(
      String activity,
      String poolId,
      String arg,
      Map<String, String> details,
      String ip,
      Map<String, String> clientDetails) {
    try {
      init(activity, poolId, arg, details, ip, clientDetails);
    } catch (Exception e) {
      log.error("", e);
    }
  }

  public ActivityCsv(
      String activity,
      String poolId,
      String arg,
      Map<String, String> details,
      HttpServletRequest request) {
    try {
      this.clientDetails = computeClientDetails(request).toString();
      init(activity, poolId, arg, details, request != null ? request.getRemoteAddr() : null, null);
    } catch (Exception e) {
      log.error("", e);
    }
  }

  public ActivityCsv(
      String activity,
      String poolId,
      Map<String, String> details,
      String ip,
      Map<String, String> clientDetails) {
    try {
      init(activity, poolId, null, details, ip, clientDetails);
    } catch (Exception e) {
      log.error("", e);
    }
  }

  public ActivityCsv(
      String activity,
      String poolId,
      RegisteredInput registeredInput,
      Map<String, String> details,
      Map<String, String> clientDetailsParam) {
    try {
      // arg
      TxOutPoint outPoint = registeredInput.getOutPoint();
      String arg = outPoint.getHash() + ":" + outPoint.getIndex();

      // details
      if (details == null) {
        details = new LinkedHashMap<>();
      }
      details.put("confs", Integer.toString(outPoint.getConfirmations()));
      details.put("value", Long.toString(outPoint.getValue()));

      // clientDetails
      Map<String, String> clientDetails = new LinkedHashMap<>();
      clientDetails.putAll(clientDetailsParam);
      clientDetails.put("u", registeredInput.getUsername());
      if (registeredInput.getLastUserHash() != null) {
        clientDetails.put("uh", registeredInput.getLastUserHash());
      }

      init(activity, poolId, arg, details, registeredInput.getIp(), clientDetails);
    } catch (Exception e) {
      log.error("", e);
    }
  }

  private void init(
      String activity,
      String poolId,
      String arg,
      Map<String, String> details,
      String ip,
      Map<String, String> clientDetails) {
    this.date = new Timestamp(System.currentTimeMillis());
    this.activity = activity;
    this.poolId = poolId;
    this.arg = arg;
    this.details = details != null ? details.toString() : null;
    this.ip = ip;
    this.clientDetails = clientDetails != null ? clientDetails.toString() : null;
  }

  private Map<String, String> computeClientDetails(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    Map<String, String> clientDetails = new LinkedHashMap<>();
    Enumeration<String> names = request.getHeaderNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      String value = request.getHeader(name);
      clientDetails.put(name, value);
    }
    return clientDetails;
  }

  public Timestamp getDate() {
    return date;
  }

  public String getActivity() {
    return activity;
  }

  public String getArg() {
    return arg;
  }

  public String getDetails() {
    return details;
  }

  public String getPoolId() {
    return poolId;
  }

  public String getIp() {
    return ip;
  }

  public String getClientDetails() {
    return clientDetails;
  }
}
