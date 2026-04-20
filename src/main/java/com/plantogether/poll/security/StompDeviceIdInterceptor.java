package com.plantogether.poll.security;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.security.SecurityConstants;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;

@Component
public class StompDeviceIdInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        String header = accessor.getFirstNativeHeader(SecurityConstants.DEVICE_ID_HEADER);
        if (header == null || header.isBlank()) {
            throw new AccessDeniedException("Missing X-Device-Id");
        }
        UUID deviceId;
        try {
            deviceId = UUID.fromString(header.trim());
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Invalid X-Device-Id");
        }
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                deviceId.toString(), null, Collections.emptyList()));
        return message;
    }
}
