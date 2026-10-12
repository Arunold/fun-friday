package com.example.funfridaygame.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * STOMP over WebSocket (with SockJS fallback) at {@code /ws}.
 *
 * <ul>
 *   <li>{@code /app/**} → {@code @MessageMapping} controllers
 *   <li>{@code /user/queue/**} → private messages per connection (snapshots, replies, events)
 *   <li>{@code /topic/rooms/{code}/stream} → public high-frequency game events
 * </ul>
 *
 * Frames from one connection are handled in order, messages to one connection are delivered in
 * order, and the broker sends heart-beats so dead connections are detected quickly.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties properties;
    private final StompSecurityInterceptor securityInterceptor;

    public WebSocketConfig(AppProperties properties, StompSecurityInterceptor securityInterceptor) {
        this.properties = properties;
        this.securityInterceptor = securityInterceptor;
    }

    /** Sends broker heart-beats. */
    @Bean
    public ThreadPoolTaskScheduler brokerHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(properties.websocketOriginList().toArray(String[]::new))
                .withSockJS();
        registry.setPreserveReceiveOrder(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(brokerHeartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(securityInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setMessageSizeLimit(256 * 1024)
                .setSendBufferSizeLimit(1024 * 1024)
                .setSendTimeLimit(20_000);
    }
}
