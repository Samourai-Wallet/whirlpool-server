package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.RegisterOutputRequest;
import com.samourai.whirlpool.protocol.rest.RestErrorResponse;
import com.samourai.whirlpool.server.services.BlameService;
import com.samourai.whirlpool.server.services.DbService;
import com.samourai.whirlpool.server.services.RegisterOutputService;
import com.samourai.whirlpool.server.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.invoke.MethodHandles;

@RestController
public class RegisterOutputController extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private RegisterOutputService registerOutputService;
  private BlameService blameService;
  private DbService dbService;

  @Autowired
  public RegisterOutputController(RegisterOutputService registerOutputService, DbService dbService) {
    this.registerOutputService = registerOutputService;
    this.dbService = dbService;
  }

  @RequestMapping(value = WhirlpoolProtocol.ENDPOINT_REGISTER_OUTPUT, method = RequestMethod.POST)
  public void registerOutput(@RequestBody RegisterOutputRequest payload) throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("[controller] " + WhirlpoolProtocol.ENDPOINT_REGISTER_OUTPUT + ": payload=" + Utils.toJsonString(payload));
    }

    // register output
    byte[] unblindedSignedBordereau = Utils.decodeBase64(payload.unblindedSignedBordereau64);
    registerOutputService.registerOutput(payload.inputsHash, unblindedSignedBordereau, payload.receiveAddress);
  }

  @ExceptionHandler
  public ResponseEntity<RestErrorResponse> handleException(Exception exception) {
    return super.handleException(exception);
  }
}