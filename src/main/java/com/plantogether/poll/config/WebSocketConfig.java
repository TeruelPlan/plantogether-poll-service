package com.plantogether.poll.config;

import com.plantogether.poll.security.StompDeviceIdInterceptor;
import com.plantogether.poll.security.StompMembershipInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompDeviceIdInterceptor deviceIdInterceptor;
    private final StompMembershipInterceptor membershipInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(StompDeviceIdInterceptor deviceIdInterceptor,
                           StompMembershipInterceptor membershipInterceptor,
                           @Value("${websocket.allowed-origins:http://localhost:*}") String allowedOrigins) {
        this.deviceIdInterceptor = deviceIdInterceptor;
        this.membershipInterceptor = membershipInterceptor;
        this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Distinct URL from chat-service /ws (Epic 7 will consolidate).
        registry.addEndpoint("/ws-poll")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Order matters: auth first, then membership.
        registration.interceptors(deviceIdInterceptor, membershipInterceptor);
    }
}
