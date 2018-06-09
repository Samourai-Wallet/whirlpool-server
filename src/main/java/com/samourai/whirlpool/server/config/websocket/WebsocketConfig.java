package com.samourai.whirlpool.server.config.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.lang.invoke.MethodHandles;
import java.util.List;

/**
 * Websocket configuration with STOMP.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfig extends AbstractWebSocketMessageBrokerConfigurer {
    private static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WhirlpoolProtocol whirlpoolProtocol;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(
                "/", // connection endpoint
                whirlpoolProtocol.ENDPOINT_REGISTER_INPUT,
                WhirlpoolProtocol.ENDPOINT_REVEAL_OUTPUT,
                WhirlpoolProtocol.ENDPOINT_SIGNING)
            // assign a random username as principal for each websocket client
            // this is needed to be able to communicate with a specific client
            .setHandshakeHandler(new AssignPrincipalWebsocketHandler())
            .setAllowedOrigins("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(whirlpoolProtocol.SOCKET_SUBSCRIBE_QUEUE, whirlpoolProtocol.SOCKET_SUBSCRIBE_USER_REPLY);
        registry.setUserDestinationPrefix(whirlpoolProtocol.SOCKET_SUBSCRIBE_USER_PRIVATE);
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        converter.setContentTypeResolver(resolver);
        messageConverters.add(converter);
        return false;
    }

    // listeners for logging purpose

    @EventListener
    public void handleSubscribeEvent(SessionSubscribeEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("[event] subscribe: username="+event.getUser().getName()+", event="+event);
        }
    }

    @EventListener
    public void handleConnectEvent(SessionConnectEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("[event] connect: username="+event.getUser().getName()+", event="+event);
        }
    }

    @EventListener
    public void handleDisconnectEvent(SessionDisconnectEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("[event] disconnect: username="+event.getUser().getName()+", event="+event);
        }
    }
}