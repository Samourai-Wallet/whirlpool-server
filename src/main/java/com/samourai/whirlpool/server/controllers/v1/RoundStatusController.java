package com.samourai.whirlpool.server.controllers.v1;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.services.RoundService;
import com.samourai.whirlpool.server.services.WebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.lang.invoke.MethodHandles;
import java.security.Principal;

@Controller
public class RoundStatusController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private RoundService roundService;
  private WebSocketService webSocketService;

  @Autowired
  public RoundStatusController(RoundService roundService, WebSocketService webSocketService) {
    this.roundService = roundService;
    this.webSocketService = webSocketService;
  }

  @MessageMapping(WhirlpoolProtocol.ENDPOINT_ROUND_STATUS)
  public void roundStatus(Principal principal) throws Exception {
    String username = principal.getName();
    if (log.isDebugEnabled()) {
      log.info("[controller] /roundStatus: username=" + username);
    }

    // return roundStatus
    try {
      this.webSocketService.sendPrivate(username, roundService.computeRoundStatusNotification());
    }
    catch(Exception e) {
      log.error("", e);
    }
  }

  @MessageExceptionHandler
  public void handleException(Exception exception, Principal principal) {
    String username = principal.getName();
    webSocketService.sendPrivateError(username, exception);
  }

}