package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.RegisterOutputRequest;
import com.samourai.whirlpool.server.services.BlameService;
import com.samourai.whirlpool.server.services.DbService;
import com.samourai.whirlpool.server.services.RegisterOutputService;
import com.samourai.whirlpool.server.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.invoke.MethodHandles;

@RestController
public class RegisterOutputController extends AbstractRestController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private RegisterOutputService registerOutputService;
  private BlameService blameService;
  private DbService dbService;

  @Autowired
  public RegisterOutputController(RegisterOutputService registerOutputService, BlameService blameService, DbService dbService) {
    this.registerOutputService = registerOutputService;
    this.blameService = blameService;
    this.dbService = dbService;
  }

  @RequestMapping(value = WhirlpoolProtocol.ENDPOINT_REGISTER_OUTPUT, method = RequestMethod.POST)
  public void registerOutput(@RequestBody RegisterOutputRequest payload) throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("[controller] " + WhirlpoolProtocol.ENDPOINT_REGISTER_OUTPUT + ": payload=" + Utils.toJsonString(payload));
    }

    // verify receiveAddress not revoked
    if (dbService.hasRevokedReceiveAddress(payload.receiveAddress)) {
      throw new IllegalArgumentException("ReceiveAddress already used");
    }

    // verify receiveAddress not banned
    if (blameService.isBannedReceiveAddress(payload.receiveAddress)) {
      log.warn("Rejecting banned receiveAddress: " + payload.receiveAddress);
      throw new IllegalArgumentException("Banned from service");
    }

    // register output
    registerOutputService.registerOutput(payload.inputsHash, payload.unblindedSignedBordereau, payload.receiveAddress);

    // revoke receiveAddress
    dbService.revokeReceiveAddress(payload.receiveAddress);
  }
}