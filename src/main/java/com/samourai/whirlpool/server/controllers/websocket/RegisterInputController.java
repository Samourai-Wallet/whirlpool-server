package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.websocket.messages.RegisterInputRequest;
import com.samourai.whirlpool.server.services.RegisterInputService;
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
public class RegisterInputController extends AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private RegisterInputService registerInputService;

  @Autowired
  public RegisterInputController(
      WebSocketService webSocketService, RegisterInputService registerInputService) {
    super(webSocketService);
    this.registerInputService = registerInputService;
  }

  @MessageMapping(WhirlpoolEndpoint.WS_REGISTER_INPUT)
  public void registerInput(
      @Payload RegisterInputRequest payload, Principal principal, StompHeaderAccessor headers)
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

    // register input in pool
    registerInputService.registerInput(
        payload.poolId,
        username,
        payload.signature,
        payload.utxoHash,
        payload.utxoIndex,
        payload.liquidity,
        payload.testMode);
  }

  @MessageExceptionHandler
  public void handleException(Exception exception, Principal principal) {
    super.handleException(exception, principal);
  }
}
