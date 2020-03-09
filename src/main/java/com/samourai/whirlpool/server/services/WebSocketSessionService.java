package com.samourai.whirlpool.server.services;

import com.samourai.whirlpool.server.utils.MessageListener;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class WebSocketSessionService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private TaskExecutor taskExecutor;
  private List<MessageListener<String>> onDisconnectListeners;

  @Autowired
  public WebSocketSessionService(TaskExecutor taskExecutor) {
    this.onDisconnectListeners = new ArrayList<>();
    this.taskExecutor = taskExecutor;
  }

  public void addOnDisconnectListener(MessageListener<String> listener) {
    onDisconnectListeners.add(listener);
  }

  public void onConnect(String username) {
    if (log.isTraceEnabled()) {
      log.trace("(<) " + username + " connect");
    }
  }

  public void onDisconnect(String username) {
    if (log.isTraceEnabled()) {
      log.trace("(<) " + username + ": disconnect");
    }
    // run in new thread for non-blocking websocket
    taskExecutor.execute(
        () -> {
          for (MessageListener<String> listener : onDisconnectListeners) {
            listener.onMessage(username);
          }
        });
  }
}
