package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.websocket.messages.RegisterInputRequest;
import com.samourai.whirlpool.server.beans.RegisteredInput;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.config.websocket.IpHandshakeInterceptor;
import com.samourai.whirlpool.server.exceptions.AlreadyRegisteredInputException;
import com.samourai.whirlpool.server.services.ExportService;
import com.samourai.whirlpool.server.services.RegisterInputService;
import com.samourai.whirlpool.server.services.WebSocketService;
import java.lang.invoke.MethodHandles;
import java.security.Principal;
import java.util.Map;
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
      WebSocketService webSocketService,
      ExportService exportService,
      RegisterInputService registerInputService) {
    super(webSocketService, exportService);
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
      RegisteredInput registeredInput =
          registerInputService.registerInput(
              payload.poolId,
              username,
              payload.signature,
              payload.utxoHash,
              payload.utxoIndex,
              payload.liquidity,
              ip);

      // log activity
      Map<String, String> clientDetails = computeClientDetails(messageHeaderAccessor);
      ActivityCsv activityCsv =
          new ActivityCsv("REGISTER_INPUT", payload.poolId, registeredInput, null, clientDetails);
      getExportService().exportActivity(activityCsv);
    } catch (AlreadyRegisteredInputException e) {
      // silent error
      log.warn("", e);
    }
  }

  @MessageExceptionHandler
  public void handleException(
      Exception exception, Principal principal, SimpMessageHeaderAccessor messageHeaderAccessor) {
    super.handleException(exception, principal, messageHeaderAccessor, "REGISTER_INPUT:ERROR");
  }
}
