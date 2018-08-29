package com.samourai.whirlpool.server.config.security;

import com.samourai.whirlpool.server.config.websocket.WebSocketConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry;
import org.springframework.security.config.annotation.web.socket.AbstractSecurityWebSocketMessageBrokerConfigurer;

@Configuration
public class WebSocketSecurityConfig extends AbstractSecurityWebSocketMessageBrokerConfigurer {

    @Override
    protected void configureInbound(MessageSecurityMetadataSourceRegistry messages) {
        super.configureInbound(messages);

        messages

        // allow websocket server endpoints
        .simpMessageDestMatchers(WebSocketConfig.WEBSOCKET_ENDPOINTS).permitAll()

        // deny any other messages (including client-to-client)
        .simpMessageDestMatchers("/**").denyAll();
    }

    @Override
    protected boolean sameOriginDisabled() {
        return true;
    }
}