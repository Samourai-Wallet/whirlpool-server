package com.samourai.whirlpool.server.config.websocket;

import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

public class IpHandshakeInterceptor implements HandshakeInterceptor {
  private static final String ATTR_IP = "ip";

  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes)
      throws Exception {

    // Set ip attribute to WebSocket session
    attributes.put(ATTR_IP, request.getRemoteAddress().toString());
    return true;
  }

  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {}

  public static String getIp(SimpMessageHeaderAccessor messageHeaderAccessor) {
    return (String)
        messageHeaderAccessor.getSessionAttributes().get(IpHandshakeInterceptor.ATTR_IP);
  }
}
