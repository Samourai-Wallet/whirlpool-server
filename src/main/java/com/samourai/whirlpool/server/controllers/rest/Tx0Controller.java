package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.wallet.api.backend.beans.MultiAddrResponse;
import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.Tx0DataResponse;
import com.samourai.whirlpool.server.beans.PoolFee;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.BackendService;
import com.samourai.whirlpool.server.services.FeeValidationService;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Tx0Controller extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private PoolService poolService;
  private FeeValidationService feeValidationService;
  private WhirlpoolServerConfig serverConfig;
  private BackendService backendService;

  @Autowired
  public Tx0Controller(
      PoolService poolService,
      FeeValidationService feeValidationService,
      WhirlpoolServerConfig serverConfig,
      BackendService backendService) {
    this.poolService = poolService;
    this.feeValidationService = feeValidationService;
    this.serverConfig = serverConfig;
    this.backendService = backendService;
  }

  @RequestMapping(value = WhirlpoolEndpoint.REST_TX0_DATA, method = RequestMethod.GET)
  public Tx0DataResponse tx0Data(
      @RequestParam(value = "poolId", required = true) String poolId,
      @RequestParam(value = "scode", required = false) String scode)
      throws Exception {

    // prevent bruteforce attacks
    Thread.sleep(1000);

    PoolFee poolFee = poolService.getPool(poolId).getPoolFee();

    String feePaymentCode = feeValidationService.getFeePaymentCode();
    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig =
        feeValidationService.getScodeConfigByScode(scode, System.currentTimeMillis());
    String feePayload64;
    long feeValue;
    String message;

    if (scodeConfig != null) {
      // scode found => scodeConfig.feeValuePercent
      byte[] feePayload = Utils.feePayloadShortToBytes(scodeConfig.getPayload());
      feePayload64 = WhirlpoolProtocol.encodeBytes(feePayload);
      feeValue = poolFee.computeFeeValue(scodeConfig.getFeeValuePercent());
      message = scodeConfig.getMessage();
    } else {
      // no SCODE => 100% fee
      feePayload64 = null;
      feeValue = poolFee.getFeeValue();
      message = null;
    }

    // fetch feeAddress
    int feeIndex;
    String feeAddress;
    long changeValue;
    if (feeValue > 0) {
      // fees
      feeIndex = computeFeeIndex();
      feeAddress = feeValidationService.computeFeeAddress(feeIndex);
      changeValue = 0;
    } else {
      // no fees
      feeIndex = 0;
      feeAddress = null;
      changeValue = computeChangeValue(poolFee);
    }

    if (log.isDebugEnabled()) {
      String scodeStr = !StringUtils.isEmpty(scode) ? scode : "null";
      log.debug(
          "Tx0Data: scode="
              + scodeStr
              + ", pool.feeValue="
              + poolFee.getFeeValue()
              + ", feeValue="
              + feeValue
              + ", changeValue="
              + changeValue
              + ", feeIndex="
              + feeIndex
              + ", feeAddress="
              + (feeAddress != null ? feeAddress : ""));
    }

    Tx0DataResponse tx0DataResponse =
        new Tx0DataResponse(
            feePaymentCode, feeValue, changeValue, message, feePayload64, feeAddress, feeIndex);
    return tx0DataResponse;
  }

  private int computeFeeIndex() {
    int feeIndex = 0; // fallback value when backend is not available
    try {
      MultiAddrResponse.Address address =
          this.backendService.fetchAddress(serverConfig.getSamouraiFees().getXpub());
      feeIndex = address.account_index;
    } catch (Exception e) {
      // use fallback value
      log.error("Unable to fetchAddress for samouraiFee => using feeIndex=" + feeIndex, e);
    }
    return feeIndex;
  }

  private long computeChangeValue(PoolFee poolFee) {
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
    if (log.isDebugEnabled()) {
      log.debug(
          "changeValue: random scode => feeValuePercent="
              + feeValuePercent
              + ", feeValue="
              + feeValue);
    }
    return feeValue;
  }
}
