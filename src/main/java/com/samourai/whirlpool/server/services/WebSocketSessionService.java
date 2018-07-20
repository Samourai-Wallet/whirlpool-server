package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.config.websocket.WebsocketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

@Service
public class WebSocketSessionService {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private Map<String, Map<String, WebSocketSession>> sessions;
    private RoundService roundService;

    @Autowired
    public WebSocketSessionService(RoundService roundService, WebsocketConfig websocketConfig) {
        this.roundService = roundService;
        this.sessions = new HashMap<>();

        // subscribe to websocket activity
        websocketConfig.__setWebSocketHandlerListener(this);
    }

    public synchronized void onConnect(WebSocketSession webSocketSession) {
        String sessionId = webSocketSession.getId();
        String username = webSocketSession.getPrincipal().getName();
        if (log.isDebugEnabled()) {
            log.debug("(--> "+ username + ") : connect");
        }
        Map<String, WebSocketSession> usernameSessions = sessions.get(username);
        if (usernameSessions == null) {
            usernameSessions = new HashMap<>();
            sessions.put(username, usernameSessions);
        }
        if (usernameSessions.containsKey(sessionId)) {
            log.error("session already registered for connecting client: username="+username+", sessionId="+sessionId);
        }
        else {
            usernameSessions.put(sessionId, webSocketSession);
        }
    }

    public synchronized void onDisconnect(WebSocketSession webSocketSession) {
        String sessionId = webSocketSession.getId();
        String username = webSocketSession.getPrincipal().getName();
        if (log.isDebugEnabled()) {
            log.debug("(--> "+ username + ") : disconnect (sessionId=" + sessionId + ")");
        }
        if (sessions.getOrDefault(username, new HashMap<>()).containsKey(sessionId)) {
            roundService.onClientDisconnect(username);
            sessions.getOrDefault(username, new HashMap<>()).remove(sessionId);
        }
        else {
            log.error("unknown session for disconnected client: username="+username+", sessionId="+sessionId);
        }
    }

}