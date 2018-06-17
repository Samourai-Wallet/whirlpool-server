package com.samourai.whirlpool.server.controllers.v1;

import com.samourai.whirlpool.protocol.v1.messages.RegisterOutputRequest;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
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
public class RegisterOutputController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String ENDPOINT = "/registerOutput";

  private RegisterOutputService registerOutputService;

  @Autowired
  public RegisterOutputController(RegisterOutputService registerOutputService) {
    this.registerOutputService = registerOutputService;
  }

  @RequestMapping(value = ENDPOINT, method = RequestMethod.POST)
  public void registerOutput(@RequestBody RegisterOutputRequest payload) throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("[controller] /registerInput: payload=" + Utils.toJsonString(payload));
    }

    validate(payload);

    // register output
    registerOutputService.registerOutput(payload.roundId, payload.unblindedSignedBordereau, payload.bordereau, payload.sendAddress, payload.receiveAddress);
  }

  private void validate(RegisterOutputRequest registerOutputRequest) throws IllegalInputException {
    if (registerOutputRequest.sendAddress.equals(registerOutputRequest.receiveAddress)) {
      throw new IllegalInputException("receiveAddress should be different than sendAddress");
    }
  }

}