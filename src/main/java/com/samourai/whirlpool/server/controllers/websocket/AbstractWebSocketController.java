package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.services.WebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.lang.invoke.MethodHandles;
import java.security.Principal;

public abstract class AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WebSocketService webSocketService;

  public AbstractWebSocketController(WebSocketService webSocketService) {
    this.webSocketService = webSocketService;
  }

  protected void validateHeaders(StompHeaderAccessor headers) throws Exception {
    String clientProtocolVersion = WhirlpoolProtocol.PROTOCOL_VERSION; // TODO headers.getFirstNativeHeader(WhirlpoolProtocol.HEADER_PROTOCOL_VERSION);
    if (!WhirlpoolProtocol.PROTOCOL_VERSION.equals(clientProtocolVersion)) {
      throw new IllegalInputException("Invalid protocol version: clientProtocolVersion=" + clientProtocolVersion + ", serverProtocolVersion=" + WhirlpoolProtocol.PROTOCOL_VERSION);
    }
  }

  protected void handleException(Exception exception, Principal principal) {
    String username = principal.getName();
    webSocketService.sendPrivateError(username, exception);
  }

  protected WebSocketService getWebSocketService() {
    return webSocketService;
  }
}