package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.config.websocket.WebSocketConfig;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

@Service
public class WebSocketSessionService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Map<String, Map<String, WebSocketSession>> sessions;
  private MixService mixService;
  private PoolService poolService;

  @Autowired
  public WebSocketSessionService(
      MixService mixService, PoolService poolService, WebSocketConfig websocketConfig) {
    this.mixService = mixService;
    this.poolService = poolService;
    this.sessions = new ConcurrentHashMap<>();

    // subscribe to websocket activity
    websocketConfig.__setWebSocketHandlerListener(this);
  }

  public synchronized void onConnect(WebSocketSession webSocketSession) {
    String sessionId = webSocketSession.getId();
    String username = webSocketSession.getPrincipal().getName();
    if (log.isDebugEnabled()) {
      log.debug("(--> " + username + ") : connect (sessionId=" + sessionId + ")");
    }
    Map<String, WebSocketSession> usernameSessions = sessions.get(username);
    if (usernameSessions == null) {
      usernameSessions = new ConcurrentHashMap<>();
      sessions.put(username, usernameSessions);
    }
    if (usernameSessions.containsKey(sessionId)) {
      log.error(
          "session already registered for connecting client: username="
              + username
              + ", sessionId="
              + sessionId);
    } else {
      usernameSessions.put(sessionId, webSocketSession);
    }
  }

  public synchronized void onDisconnect(WebSocketSession webSocketSession) {
    String sessionId = webSocketSession.getId();
    String username = webSocketSession.getPrincipal().getName();
    if (log.isDebugEnabled()) {
      log.debug("(--> " + username + ") : disconnect (sessionId=" + sessionId + ")");
    }

    Map<String, WebSocketSession> userSessions = sessions.get(username);
    if (userSessions != null && userSessions.containsKey(sessionId)) {
      mixService.onClientDisconnect(username);
      poolService.onClientDisconnect(username);
      userSessions.remove(sessionId);
    } else {
      log.error(
          "unknown session for disconnected client: username="
              + username
              + ", sessionId="
              + sessionId);
    }
  }
}
