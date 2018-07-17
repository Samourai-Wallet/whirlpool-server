package com.samourai.whirlpool.server.config.websocket;

import com.samourai.whirlpool.server.services.WebSocketSessionService;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

public class WebSocketHandler extends SubProtocolWebSocketHandler {
    private WebSocketSessionService webSocketSessionService;

    public WebSocketHandler(MessageChannel clientInboundChannel, SubscribableChannel clientOutboundChannel, WebSocketSessionService webSocketSessionService) {
        super(clientInboundChannel, clientOutboundChannel);
        this.webSocketSessionService = webSocketSessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        webSocketSessionService.onConnect(session);
        super.afterConnectionEstablished(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        webSocketSessionService.onDisconnect(session);
        super.afterConnectionClosed(session, closeStatus);
    }
}