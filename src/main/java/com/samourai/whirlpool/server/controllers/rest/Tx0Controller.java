package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.wallet.api.backend.beans.MultiAddrResponse;
import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.Tx0DataResponse;
import com.samourai.whirlpool.server.config.WhirlpoolServerConfig;
import com.samourai.whirlpool.server.services.BackendService;
import com.samourai.whirlpool.server.services.FeeValidationService;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
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
  public Tx0DataResponse tx0Data(@RequestParam(value = "scode", required = false) String scode) {
    // PoolFee poolFee = poolService.getPool(poolId);

    String feePaymentCode = feeValidationService.getFeePaymentCode();
    WhirlpoolServerConfig.ScodeSamouraiFeeConfig scodeConfig =
        feeValidationService.getScodeConfigByScode(scode, System.currentTimeMillis());
    byte[] feePayload;
    // long feeValue;
    if (scodeConfig != null) {
      // scode found => scodeConfig.feeValuePercent
      feePayload = Utils.feePayloadShortToBytes(scodeConfig.getPayload());
      // feeValue = poolFee.computeFeeValue(scodeConfig.getFeeValuePercent());
    } else {
      // no scode => 100% fee
      feePayload = null;
      // feeValue = poolFee.getFeeValue();
    }
    Tx0DataResponse tx0DataResponse;
    if (feePayload != null) {
      if (log.isDebugEnabled()) {
        log.debug("Tx0Data: scode=" + scode);
      }
      tx0DataResponse =
          new Tx0DataResponse(
              feePaymentCode,
              WhirlpoolProtocol.encodeBytes(feePayload)); // TODO !!! transmit feeValue
    } else {
      int feeIndex = 0; // fallback value when backend is not available
      try {
        MultiAddrResponse.Address address =
            this.backendService.fetchAddress(serverConfig.getSamouraiFees().getXpub());
        feeIndex = address.account_index;
      } catch (Exception e) {
        // use fallback value
        log.error("Unable to fetchAddress for samouraiFee => using feeIndex=" + feeIndex);
      }
      String feeAddress = feeValidationService.computeFeeAddress(feeIndex);
      if (log.isDebugEnabled()) {
        log.debug("Tx0Data: scode=null, feeIndex=" + feeIndex + ", feeAddress=" + feeAddress);
      }
      tx0DataResponse = new Tx0DataResponse(feePaymentCode, feeAddress, feeIndex);
    }
    return tx0DataResponse;
  }
}
