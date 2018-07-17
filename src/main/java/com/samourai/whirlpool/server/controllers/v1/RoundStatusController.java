package com.samourai.whirlpool.server.controllers.v1;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.services.RoundService;
import com.samourai.whirlpool.server.services.WebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
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

  @SubscribeMapping(WhirlpoolProtocol.SOCKET_SUBSCRIBE_USER_PRIVATE + WhirlpoolProtocol.SOCKET_SUBSCRIBE_USER_REPLY)
  public void roundStatusOnSubscribe(Principal principal) {
    String username = principal.getName();
    if (log.isDebugEnabled()) {
      log.info("[controller] subscribe:"+ WhirlpoolProtocol.SOCKET_SUBSCRIBE_USER_PRIVATE + WhirlpoolProtocol.SOCKET_SUBSCRIBE_USER_REPLY + ": username=" + username);
    }

    // return roundStatus
    try {
      Thread.sleep(1000); // wait to make sure client subscription is ready
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