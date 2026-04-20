package com.plantogether.poll.security;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.poll.grpc.client.TripGrpcClient;
import com.plantogether.trip.grpc.IsMemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StompMembershipInterceptor implements ChannelInterceptor {

    static final Pattern TRIP_TOPIC = Pattern.compile("^/topic/trips/([0-9a-fA-F-]{36})(/|$)");
    static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final TripGrpcClient tripGrpcClient;
    private final Map<Key, Instant> membershipCache = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            invalidateForUser(accessor.getUser());
            return message;
        }

        if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Matcher m = TRIP_TOPIC.matcher(destination);
        if (!m.find()) {
            // Not a trip-scoped topic; allow through.
            return message;
        }

        String tripId = m.group(1);
        Principal principal = accessor.getUser();
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("Missing authenticated principal");
        }
        String deviceId = principal.getName();

        if (!isMemberCached(tripId, deviceId)) {
            throw new AccessDeniedException("Device is not a member of this trip");
        }
        return message;
    }

    private boolean isMemberCached(String tripId, String deviceId) {
        Key key = new Key(tripId, deviceId);
        Instant expiresAt = membershipCache.get(key);
        Instant now = Instant.now();
        if (expiresAt != null && expiresAt.isAfter(now)) {
            return true;
        }
        IsMemberResponse response = tripGrpcClient.isMember(tripId, deviceId);
        if (response.getIsMember()) {
            membershipCache.put(key, now.plus(CACHE_TTL));
            return true;
        }
        return false;
    }

    // Sweep expired entries so abrupt disconnects (no DISCONNECT frame) do not leak memory indefinitely.
    @Scheduled(fixedDelayString = "PT5M")
    void evictExpired() {
        Instant now = Instant.now();
        membershipCache.entrySet().removeIf(e -> !e.getValue().isAfter(now));
    }

    private void invalidateForUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return;
        }
        String deviceId = principal.getName();
        membershipCache.keySet().removeIf(k -> k.deviceId().equals(deviceId));
    }

    record Key(String tripId, String deviceId) {
    }
}
