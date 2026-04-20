package com.plantogether.poll.security;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.poll.grpc.client.TripGrpcClient;
import com.plantogether.trip.grpc.IsMemberResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StompMembershipInterceptorTest {

    @Mock TripGrpcClient tripGrpcClient;
    @Mock MessageChannel channel;

    StompMembershipInterceptor interceptor;
    String tripId;
    String deviceId;

    @BeforeEach
    void setUp() {
        interceptor = new StompMembershipInterceptor(tripGrpcClient);
        tripId = UUID.randomUUID().toString();
        deviceId = UUID.randomUUID().toString();
    }

    private Message<byte[]> subscribe(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(deviceId, null, Collections.emptyList()));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void subscribe_member_permitsMessage() {
        when(tripGrpcClient.isMember(tripId, deviceId))
                .thenReturn(IsMemberResponse.newBuilder().setIsMember(true).setRole("PARTICIPANT").build());

        Message<?> result = interceptor.preSend(subscribe("/topic/trips/" + tripId + "/updates"), channel);

        assertNotNull(result);
        verify(tripGrpcClient, times(1)).isMember(tripId, deviceId);
    }

    @Test
    void subscribe_nonMember_throwsAccessDenied() {
        when(tripGrpcClient.isMember(tripId, deviceId))
                .thenReturn(IsMemberResponse.newBuilder().setIsMember(false).setRole("").build());

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(subscribe("/topic/trips/" + tripId + "/updates"), channel));
    }

    @Test
    void subscribe_malformedDestination_passesThrough() {
        Message<?> result = interceptor.preSend(subscribe("/topic/system/metrics"), channel);

        assertNotNull(result);
        verifyNoInteractions(tripGrpcClient);
    }

    @Test
    void subscribe_cacheHit_doesNotCallGrpc() {
        when(tripGrpcClient.isMember(tripId, deviceId))
                .thenReturn(IsMemberResponse.newBuilder().setIsMember(true).setRole("PARTICIPANT").build());

        interceptor.preSend(subscribe("/topic/trips/" + tripId + "/updates"), channel);
        interceptor.preSend(subscribe("/topic/trips/" + tripId + "/updates"), channel);

        verify(tripGrpcClient, times(1)).isMember(tripId, deviceId);
    }

    @Test
    void subscribe_missingPrincipal_throwsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/trips/" + tripId + "/updates");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
    }
}
