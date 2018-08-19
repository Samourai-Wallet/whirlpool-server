package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.messages.RegisterInputRequest;
import com.samourai.whirlpool.server.exceptions.QueueInputException;
import com.samourai.whirlpool.server.exceptions.UnconfirmedInputException;
import com.samourai.whirlpool.server.services.RegisterInputService;
import com.samourai.whirlpool.server.services.WebSocketService;
import com.samourai.whirlpool.server.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.lang.invoke.MethodHandles;
import java.security.Principal;

@Controller
public class RegisterInputController extends AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private RegisterInputService registerInputService;

  @Autowired
  public RegisterInputController(WebSocketService webSocketService, RegisterInputService registerInputService) {
    super(webSocketService);
    this.registerInputService = registerInputService;
  }

  @MessageMapping(WhirlpoolProtocol.ENDPOINT_REGISTER_INPUT)
  public void registerInputs(@Payload RegisterInputRequest payload, Principal principal, StompHeaderAccessor headers) throws Exception {
    validateHeaders(headers);

    String username = principal.getName();
    if (log.isDebugEnabled()) {
      log.debug("[controller] " + WhirlpoolProtocol.ENDPOINT_REGISTER_INPUT + ": username=" + username + ", payload=" + Utils.toJsonString(payload));
    }

    try {
      // register inputs and send back signed bordereau
      registerInputService.registerInput(payload.mixId, username, payload.pubkey, payload.signature, payload.blindedBordereau, payload.utxoHash, payload.utxoIndex, payload.liquidity);
    }
    catch(UnconfirmedInputException e) {
      log.info("Placing unconfirmed input on queue: " + payload.utxoHash+":"+payload.utxoIndex, e.getMessage());
    }
    catch(QueueInputException e) {
      log.info("Placing input on queue: " + payload.utxoHash+":"+payload.utxoIndex, e.getMessage());
    }
  }

  @MessageExceptionHandler
  public void handleException(Exception exception, Principal principal) {
    super.handleException(exception, principal);
  }

}