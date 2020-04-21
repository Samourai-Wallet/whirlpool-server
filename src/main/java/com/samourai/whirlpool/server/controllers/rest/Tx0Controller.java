package com.samourai.whirlpool.server.controllers.rest;

import com.google.common.collect.ImmutableMap;
import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.Tx0DataResponse;
import com.samourai.whirlpool.server.beans.PoolFee;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.ExportService;
import com.samourai.whirlpool.server.services.FeePayloadService;
import com.samourai.whirlpool.server.services.FeeValidationService;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.utils.Utils;
import com.samourai.xmanager.client.XManagerClient;
import com.samourai.xmanager.protocol.XManagerService;
import com.samourai.xmanager.protocol.rest.AddressIndexResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class Tx0Controller extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private PoolService poolService;
  private FeeValidationService feeValidationService;
    private FeePayloadService feePayloadService;
    private ExportService exportService;
  private WhirlpoolServerConfig serverConfig;
  private XManagerClient xManagerClient;

  @Autowired
  public Tx0Controller(
      PoolService poolService,
      FeeValidationService feeValidationService,
      FeePayloadService feePayloadService,
      ExportService exportService,
      WhirlpoolServerConfig serverConfig,
      XManagerClient xManagerClient) {
    this.poolService = poolService;
    this.feeValidationService = feeValidationService;
    this.feePayloadService = feePayloadService;
    this.exportService = exportService;
    this.serverConfig = serverConfig;
    this.xManagerClient = xManagerClient;
  }

  @RequestMapping(value = WhirlpoolEndpoint.REST_TX0_DATA, method = RequestMethod.GET)
  public Tx0DataResponse tx0Data(
      HttpServletRequest request,
      @RequestParam(value = "poolId", required = true) String poolId,
      @RequestParam(value = "scode", required = false) String scode)
      throws Exception {

    // prevent bruteforce attacks
    Thread.sleep(1000);

    if (StringUtils.isEmpty(scode) || "null".equals(scode)) {
      scode = null;
    }

    PoolFee poolFee = poolService.getPool(poolId).getPoolFee();

    String feePaymentCode = feeValidationService.getFeePaymentCode();
    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig =
        feeValidationService.getScodeConfigByScode(scode, System.currentTimeMillis());
    short scodePayload;
    short partnerPayload = 0;
    long feeValue;
    int feeDiscountPercent;
    String message;

    if (scodeConfig != null) {
      // scode found => scodeConfig.feeValuePercent
      scodePayload = scodeConfig.getPayload();
      feeValue = poolFee.computeFeeValue(scodeConfig.getFeeValuePercent());
      feeDiscountPercent = 100 - scodeConfig.getFeeValuePercent();
      message = scodeConfig.getMessage();
    } else {
      // no SCODE => 100% fee
      scodePayload = 0;
      feeValue = poolFee.getFeeValue();
      feeDiscountPercent = 0;
      message = null;

      if (scode != null) {
        log.warn("Invalid SCODE: " + scode);
      }
    }

    // fetch feeAddress
    int feeIndex;
    String feeAddress;
    long feeChange;
    if (feeValue > 0) {
      // fees
      AddressIndexResponse addressIndexResponse =
          xManagerClient.getAddressIndexOrDefault(XManagerService.WHIRLPOOL);
      feeIndex = addressIndexResponse.index;
      feeAddress = addressIndexResponse.address;
      feeChange = 0;
    } else {
      // no fees
      feeIndex = 0;
      feeAddress = null;
      feeChange = computeRandomFeeChange(poolFee);
    }

    byte[] feePayload = feePayloadService.encodeFeePayload(feeIndex, scodePayload, partnerPayload);
    String feePayload64 = WhirlpoolProtocol.encodeBytes(feePayload);

    if (log.isDebugEnabled()) {
      String scodeStr = !StringUtils.isEmpty(scode) ? scode : "null";
      log.debug(
          "Tx0Data: scode="
              + scodeStr
              + ", pool.feeValue="
              + poolFee.getFeeValue()
              + ", feeValue="
              + feeValue
              + ", feeChange="
              + feeChange
              + ", feeIndex="
              + feeIndex
              + ", feeAddress="
              + (feeAddress != null ? feeAddress : ""));
    }

    // log activity
    Map<String, String> details = ImmutableMap.of("scode", (scode != null ? scode : "null"));
    ActivityCsv activityCsv = new ActivityCsv("TX0", poolId, null, details, request);
    exportService.exportActivity(activityCsv);

    Tx0DataResponse tx0DataResponse =
        new Tx0DataResponse(
            feePaymentCode,
            feeValue,
            feeChange,
            feeDiscountPercent,
            message,
            feePayload64,
            feeAddress);
    return tx0DataResponse;
  }

  private long computeRandomFeeChange(PoolFee poolFee) {
    // random SCODE
    List<WhirlpoolServerConfig.ScodeSamouraiFeeConfig> nonZeroScodes =
        serverConfig
            .getSamouraiFees()
            .getScodes()
            .values()
            .stream()
            .filter(c -> c.getFeeValuePercent() > 0)
            .collect(Collectors.toList());
    if (nonZeroScodes.isEmpty()) {
      // no SCODE available => use 100% pool fee
      long feeValue = poolFee.getFeeValue();
      if (log.isDebugEnabled()) {
        log.debug("changeValue: no scode available => feeValuePercent=100, feeValue=" + feeValue);
      }
      return feeValue;
    }

    // use random SCODE
    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig = Utils.getRandomEntry(nonZeroScodes);
    int feeValuePercent = scodeConfig.getFeeValuePercent();
    long feeValue = poolFee.computeFeeValue(feeValuePercent);
    return feeValue;
  }
}
