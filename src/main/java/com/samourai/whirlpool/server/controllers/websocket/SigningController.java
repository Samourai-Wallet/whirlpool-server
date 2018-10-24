package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.messages.SigningRequest;
import com.samourai.whirlpool.server.services.SigningService;
import com.samourai.whirlpool.server.services.WebSocketService;
import com.samourai.whirlpool.server.utils.Utils;
import java.lang.invoke.MethodHandles;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class SigningController extends AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private SigningService signingService;

  @Autowired
  public SigningController(WebSocketService webSocketService, SigningService signingService) {
    super(webSocketService);
    this.signingService = signingService;
  }

  @MessageMapping(WhirlpoolProtocol.ENDPOINT_SIGNING)
  public void signing(
      @Payload SigningRequest payload, Principal principal, StompHeaderAccessor headers)
      throws Exception {
    validateHeaders(headers);

    String username = principal.getName();
    if (log.isDebugEnabled()) {
      log.debug(
          "[controller] "
              + headers.getDestination()
              + ": username="
              + username
              + ", payload="
              + Utils.toJsonString(payload));
    }

    // signing
    byte[][] witness = computeWitness(payload.witnesses64);
    signingService.signing(payload.mixId, username, witness);
  }

  private byte[][] computeWitness(String[] witnesses64) {
    byte[][] witnesses = new byte[witnesses64.length][];
    for (int i = 0; i < witnesses64.length; i++) {
      String witness64 = witnesses64[i];
      witnesses[i] = Utils.decodeBase64(witness64);
    }
    return witnesses;
  }

  @MessageExceptionHandler
  public void handleException(Exception exception, Principal principal) {
    super.handleException(exception, principal);
  }
}
