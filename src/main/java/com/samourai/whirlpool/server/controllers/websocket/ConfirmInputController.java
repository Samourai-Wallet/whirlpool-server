package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.messages.ConfirmInputRequest;
import com.samourai.whirlpool.server.services.ConfirmInputService;
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
public class ConfirmInputController extends AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private ConfirmInputService confirmInputService;

  @Autowired
  public ConfirmInputController(
      WebSocketService webSocketService, ConfirmInputService confirmInputService) {
    super(webSocketService);
    this.confirmInputService = confirmInputService;
  }

  @MessageMapping(WhirlpoolEndpoint.WS_CONFIRM_INPUT)
  public void confirmInput(
      @Payload ConfirmInputRequest payload, Principal principal, StompHeaderAccessor headers)
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

    // confirm input and send back signed bordereau, or enqueue back to pool
    byte[] blindedBordereau = WhirlpoolProtocol.decodeBytes(payload.blindedBordereau64);
    confirmInputService.confirmInputOrQueuePool(payload.mixId, username, blindedBordereau);
  }

  @MessageExceptionHandler
  public void handleException(Exception exception, Principal principal) {
    super.handleException(exception, principal);
  }
}
