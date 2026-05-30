package com.example.demo.config;

import com.example.demo.security.WsJwtChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP/WebSocket layer used for all real-time push: disappearing message
 * deletions, presence, session revocation, security alerts.
 *
 * <h3>Topics</h3>
 * <ul>
 *   <li>{@code /topic/user/{userId}} — user-scoped events (message.deleted,
 *       message.read, message.expired, session.revoked, secure.alert)</li>
 *   <li>{@code /topic/admin} — admin-only realtime feed</li>
 *   <li>{@code /topic/room/{roomId}} — room-scoped events</li>
 * </ul>
 *
 * <h3>Auth</h3>
 * Every STOMP CONNECT must include {@code Authorization: Bearer <jwt>} as a
 * header. {@link WsJwtChannelInterceptor} validates it and pins the principal
 * on the session so subsequent SEND / SUBSCRIBE frames are authorized.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class SecureWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WsJwtChannelInterceptor wsJwtChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // In-memory broker. Swap for STOMP/relay broker (RabbitMQ / ActiveMQ) at scale.
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        registry.addEndpoint("/ws-native")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(wsJwtChannelInterceptor);
    }
}
