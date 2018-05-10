package com.samourai.whirlpool.server.controllers.v1;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.v1.messages.RegisterInputRequest;
import com.samourai.whirlpool.server.services.RegisterInputService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.lang.invoke.MethodHandles;
import java.security.Principal;

@Controller
public class RegisterInputController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private RegisterInputService registerInputService;

  @Autowired
  public RegisterInputController(RegisterInputService registerInputService) {
    this.registerInputService = registerInputService;
  }

  @MessageMapping(WhirlpoolProtocol.ENDPOINT_REGISTER_INPUT)
  public void registerInputs(@Payload RegisterInputRequest payload, Principal principal) throws Exception {
    String username = principal.getName();
    log.info("/registerInput: username="+username+", payload="+payload);

    // register inputs and send back signed bordereau
    registerInputService.registerInput(payload.roundId, username, payload.pubkey, payload.signature, payload.blindedBordereau,  payload.utxoHash, payload.utxoIndex, payload.paymentCode, payload.liquidity);
  }

}