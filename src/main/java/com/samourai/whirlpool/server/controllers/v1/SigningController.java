package com.samourai.whirlpool.server.controllers.v1;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.v1.messages.SigningRequest;
import com.samourai.whirlpool.server.services.SigningService;
import com.samourai.whirlpool.server.services.WebSocketService;
import com.samourai.whirlpool.server.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.lang.invoke.MethodHandles;
import java.security.Principal;

@Controller
public class SigningController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WebSocketService webSocketService;
  private SigningService signingService;

  @Autowired
  public SigningController(WebSocketService webSocketService, SigningService signingService) {
    this.webSocketService = webSocketService;
    this.signingService = signingService;
  }

  @MessageMapping(WhirlpoolProtocol.ENDPOINT_SIGNING)
  public void signing(@Payload SigningRequest payload, Principal principal) throws Exception {
    String username = principal.getName();
    if (log.isDebugEnabled()) {
      log.debug("[controller] " + WhirlpoolProtocol.ENDPOINT_SIGNING + ": username=" + username + ", payload=" + Utils.toJsonString(payload));
    }

    // signing
    signingService.signing(payload.roundId, username, payload.witness);
  }

  @MessageExceptionHandler
  public void handleException(Exception exception, Principal principal) {
    String username = principal.getName();
    webSocketService.sendPrivateError(username, exception);
  }

}