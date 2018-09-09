package com.samourai.whirlpool.server.controllers.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.beans.Pool;
import com.samourai.whirlpool.server.services.MixService;
import com.samourai.whirlpool.server.services.PoolService;
import com.samourai.whirlpool.server.services.WebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.lang.invoke.MethodHandles;
import java.security.Principal;

@Controller
public class MixStatusController extends AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private MixService mixService;
  private PoolService poolService;

  @Autowired
  public MixStatusController(MixService mixService, PoolService poolService, WebSocketService webSocketService) {
    super(webSocketService);
    this.mixService = mixService;
    this.poolService = poolService;
  }

  @SubscribeMapping(WhirlpoolProtocol.SOCKET_SUBSCRIBE_USER_PRIVATE + WhirlpoolProtocol.SOCKET_SUBSCRIBE_USER_REPLY)
  public void subscribePrivate(Principal principal, StompHeaderAccessor headers) throws Exception {
    // nothing to do here
    // no header version check here, to be able to send error notifications to outdated clients

    if (log.isDebugEnabled()) {
      String username = principal.getName();
      log.info("[controller] subscribe:"+ headers.getDestination() + ": username=" + username);
    }
  }

  @SubscribeMapping(WhirlpoolProtocol.SOCKET_SUBSCRIBE_QUEUE)
  public void mixStatusOnSubscribeQueue(Principal principal, StompHeaderAccessor headers) throws Exception {
    validateHeaders(headers);

    String username = principal.getName();
    if (log.isDebugEnabled()) {
      log.info("[controller] subscribe:"+ headers.getDestination() + ": username=" + username);
    }

    // validate poolId
    String headerPoolId = headers.getFirstNativeHeader(WhirlpoolProtocol.HEADER_POOL_ID);
    Pool pool = poolService.getPool(headerPoolId); // exception if not found

    try {
      String mixId = pool.getCurrentMix().getMixId();
      getWebSocketService().sendPrivate(username, mixService.computeMixStatusNotification(mixId));
    }
    catch(Exception e) {
      log.error("", e);
    }
  }

  @MessageExceptionHandler
  public void handleException(Exception exception, Principal principal) {
    super.handleException(exception, principal);
  }

}