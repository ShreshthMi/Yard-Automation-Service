package com.frauscher.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Clients connect to /ws (STOMP over SockJS) and subscribe to
 * /topic/yard/{yardName} - e.g. /topic/yard/YARD1 - to receive only that
 * yard's messages. Subscribing to one yard's topic structurally cannot
 * receive another yard's traffic; no manual filtering needed.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        //registry.addEndpoint("/ws").withSockJS();
    	
    	// Allows the standalone HTML test viewer (opened via file://, origin "null") and
        // any other local dev client to connect. Tighten this to specific origins for production.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}
