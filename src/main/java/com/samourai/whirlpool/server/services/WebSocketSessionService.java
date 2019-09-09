package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.utils.MessageListener;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WebSocketSessionService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Map<String, Boolean> sessions;
  private List<MessageListener<String>> onDisconnectListeners;

  @Autowired
  public WebSocketSessionService() {
    this.onDisconnectListeners = new ArrayList<>();
    this.sessions = new ConcurrentHashMap<>();
  }

  public void addOnDisconnectListener(MessageListener<String> listener) {
    onDisconnectListeners.add(listener);
  }

  public synchronized void onConnect(String username) {
    if (log.isTraceEnabled()) {
      log.trace("(--> " + username + ") : connect");
    }
    if (!sessions.containsKey(username)) {
      sessions.put(username, Boolean.TRUE);
    } else {
      log.error("session already registered for connecting client: username=" + username);
    }
  }

  public synchronized void onDisconnect(String username) {
    if (log.isTraceEnabled()) {
      log.trace("(--> " + username + ") : disconnect");
    }

    if (sessions.containsKey(username)) {
      for (MessageListener<String> listener : onDisconnectListeners) {
        listener.onMessage(username);
      }
      sessions.remove(username);
    } else {
      log.error("unknown session for disconnected client: username=" + username);
    }
  }
}
