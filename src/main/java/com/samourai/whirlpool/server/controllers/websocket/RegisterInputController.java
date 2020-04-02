package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.websocket.messages.RegisterInputRequest;
import com.samourai.whirlpool.server.config.websocket.IpHandshakeInterceptor;
import com.samourai.whirlpool.server.exceptions.AlreadyRegisteredInputException;
import com.samourai.whirlpool.server.services.RegisterInputService;
import com.samourai.whirlpool.server.services.WebSocketService;
import java.lang.invoke.MethodHandles;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
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
      @Payload RegisterInputRequest payload,
      Principal principal,
      StompHeaderAccessor headers,
      SimpMessageHeaderAccessor messageHeaderAccessor)
      throws Exception {
    validateHeaders(headers);

    String username = principal.getName();
    String ip = IpHandshakeInterceptor.getIp(messageHeaderAccessor);
    String httpHeaders = computeHttpHeaders(messageHeaderAccessor);
    if (log.isDebugEnabled()) {
      log.debug(
          "(<) ["
              + payload.poolId
              + "] "
              + username
              + " "
              + headers.getDestination()
              + " "
              + payload.utxoHash
              + ":"
              + payload.utxoIndex);
    }

    // register input in pool
    try {
      registerInputService.registerInput(
          payload.poolId,
          username,
          payload.signature,
          payload.utxoHash,
          payload.utxoIndex,
          payload.liquidity,
          ip,
          httpHeaders);
    } catch (AlreadyRegisteredInputException e) {
      // silent error
      log.warn("", e);
    }
  }

  private String computeHttpHeaders(SimpMessageHeaderAccessor messageHeaderAccessor) {
    Map<String, List<String>> nativeHeaders =
        (Map) messageHeaderAccessor.getHeader("nativeHeaders");
    if (nativeHeaders == null) {
      return null;
    }
    String[] ignoreHeaders = new String[] {"content-type", "content-length"};
    return nativeHeaders
        .entrySet()
        .stream()
        .filter(e -> !ArrayUtils.contains(ignoreHeaders, e.getKey()))
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(Collectors.toList())
        .toString();
  }

  @MessageExceptionHandler
  public void handleException(Exception exception, Principal principal) {
    super.handleException(exception, principal);
  }
}
