package com.samourai.whirlpool.server.config.websocket;

import com.samourai.whirlpool.server.services.WebSocketSessionService;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

public class WebSocketHandler extends SubProtocolWebSocketHandler {
    private WebSocketSessionService webSocketSessionService;

    public WebSocketHandler(WebSocketConfig websocketConfig) {
        super(websocketConfig.clientInboundChannel(), websocketConfig.clientOutboundChannel());
    }

    protected void __setWebSocketSessionService(WebSocketSessionService webSocketSessionService) {
        this.webSocketSessionService = webSocketSessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (webSocketSessionService != null) {
            webSocketSessionService.onConnect(session);
        }
        super.afterConnectionEstablished(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        if (webSocketSessionService != null) {
            webSocketSessionService.onDisconnect(session);
        }
        super.afterConnectionClosed(session, closeStatus);
    }
}