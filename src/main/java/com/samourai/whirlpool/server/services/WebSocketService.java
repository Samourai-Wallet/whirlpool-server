package com.samourai.whirlpool.server.services;

import com.samourai.javaserver.utils.ServerUtils;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.messages.ErrorResponse;
import java.lang.invoke.MethodHandles;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketService {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private WhirlpoolProtocol whirlpoolProtocol;
  private SimpMessagingTemplate messagingTemplate;
  private TaskExecutor taskExecutor;

  @Autowired
  public WebSocketService(
      WhirlpoolProtocol whirlpoolProtocol,
      SimpMessagingTemplate messagingTemplate,
      TaskExecutor taskExecutor) {
    this.whirlpoolProtocol = whirlpoolProtocol;
    this.messagingTemplate = messagingTemplate;
    this.taskExecutor = taskExecutor;
    messagingTemplate.setMessageConverter(new MappingJackson2MessageConverter());
  }

  public void sendPrivate(String username, Object payload) {
    sendPrivate(Arrays.asList(username), payload);
  }

  public void sendPrivate(Collection<String> usernames, Object payload) {
    if (log.isTraceEnabled()) {
      log.trace(
          "(>) "
              + String.join(",", usernames)
              + ": "
              + ServerUtils.getInstance().toJsonString(payload));
    }
    usernames.forEach(
        username -> {
          taskExecutor.execute(
              () ->
                  messagingTemplate.convertAndSendToUser(
                      username,
                      whirlpoolProtocol.WS_PREFIX_USER_REPLY,
                      payload,
                      computeHeaders(payload)));
        });
  }

  public void sendPrivateError(String username, String message) {
    log.warn("(>) " + username + " sendPrivateError: " + message);
    ErrorResponse errorResponse = new ErrorResponse(message);
    sendPrivate(username, errorResponse);
  }

  private Map<String, Object> computeHeaders(Object payload) {
    Map<String, Object> headers = new HashMap<>();
    headers.put(whirlpoolProtocol.HEADER_MESSAGE_TYPE, payload.getClass().getName());
    headers.put(whirlpoolProtocol.HEADER_PROTOCOL_VERSION, WhirlpoolProtocol.PROTOCOL_VERSION);
    return headers;
  }
}
