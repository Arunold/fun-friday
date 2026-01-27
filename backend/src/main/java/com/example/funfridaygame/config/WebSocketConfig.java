package com.example.funfridaygame.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/stomp").setAllowedOrigins("http://localhost:4200").withSockJS();
        registry.addEndpoint("/ws").setAllowedOrigins("http://localhost:4200").withSockJS();
        registry.addEndpoint("/ws/info").setAllowedOrigins("http://localhost:4200").withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Increase message size limits for drawing data
        registration.setMessageSizeLimit(512 * 1024);     // 512 KB max message size
        registration.setSendBufferSizeLimit(1024 * 1024); // 1 MB send buffer
        registration.setSendTimeLimit(20 * 1000);         // 20 seconds send timeout
    }
}
