package com.samourai.whirlpool.server.controllers.v1;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.v1.messages.RegisterOutputRequest;
import com.samourai.whirlpool.server.services.RoundService;
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
public class RevealOutputController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WebSocketService webSocketService;
  private RoundService roundService;

  @Autowired
  public RevealOutputController(WebSocketService webSocketService, RoundService roundService) {
    this.webSocketService = webSocketService;
    this.roundService = roundService;
  }

  @MessageMapping(WhirlpoolProtocol.ENDPOINT_REVEAL_OUTPUT)
  public void revealOutput(@Payload RegisterOutputRequest payload, Principal principal) throws Exception {
    String username = principal.getName();
    if (log.isDebugEnabled()) {
      log.debug("[controller] /revealOutput: username=" + username + ", payload=" + Utils.toJsonString(payload));
    }

    // register output
    roundService.revealOutput(payload.roundId, username, payload.bordereau);
  }

  @MessageExceptionHandler
  public void handleException(Exception exception, Principal principal) {
    String username = principal.getName();
    webSocketService.sendPrivateError(username, exception);
  }

}