package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.exceptions.NotifiableException;
import com.samourai.whirlpool.server.services.WebSocketService;
import java.lang.invoke.MethodHandles;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public abstract class AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WebSocketService webSocketService;

  public AbstractWebSocketController(WebSocketService webSocketService) {
    this.webSocketService = webSocketService;
  }

  protected void validateHeaders(StompHeaderAccessor headers) throws Exception {
    String clientProtocolVersion =
        headers.getFirstNativeHeader(WhirlpoolProtocol.HEADER_PROTOCOL_VERSION);
    if (!WhirlpoolProtocol.PROTOCOL_VERSION.equals(clientProtocolVersion)) {
      throw new IllegalInputException(
          "Version mismatch: server="
              + WhirlpoolProtocol.PROTOCOL_VERSION
              + ", client="
              + (clientProtocolVersion != null ? clientProtocolVersion : "unknown"));
    }
  }

  protected void handleException(Exception e, Principal principal) {
    log.error("handleException", e);
    NotifiableException notifiable = NotifiableException.computeNotifiableException(e);
    String message = notifiable.getMessage();
    String username = principal.getName();
    webSocketService.sendPrivateError(username, message);
  }

  protected WebSocketService getWebSocketService() {
    return webSocketService;
  }
}
