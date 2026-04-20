package com.plantogether.poll.event.publisher;

import com.plantogether.common.event.PollVoteCastEvent;
import com.plantogether.poll.config.RabbitConfig;
import com.plantogether.poll.domain.VoteStatus;
import com.plantogether.poll.event.publisher.PollRealtimeBroadcaster.PollVoteCastInternalEvent;
import com.plantogether.poll.event.publisher.PollRealtimeBroadcaster.PollVoteCastMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PollRealtimeBroadcasterTest {

    @Mock SimpMessagingTemplate simpMessagingTemplate;
    @Mock RabbitTemplate rabbitTemplate;

    PollRealtimeBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new PollRealtimeBroadcaster(simpMessagingTemplate, rabbitTemplate);
    }

    @Test
    void broadcastVoteCast_sendsToStompAndRabbit() {
        UUID pollId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        broadcaster.onVoteCast(new PollVoteCastInternalEvent(
                pollId, tripId, slotId, deviceId, VoteStatus.YES, 4));

        ArgumentCaptor<Object> stompPayload = ArgumentCaptor.forClass(Object.class);
        verify(simpMessagingTemplate).convertAndSend(
                eqStr("/topic/trips/" + tripId + "/updates"),
                stompPayload.capture());
        PollVoteCastMessage stomp = (PollVoteCastMessage) stompPayload.getValue();
        assertEquals("POLL_VOTE_CAST", stomp.type());
        assertEquals(pollId.toString(), stomp.pollId());
        assertEquals(slotId.toString(), stomp.slotId());
        assertEquals(deviceId.toString(), stomp.deviceId());
        assertEquals("YES", stomp.status());
        assertEquals(4, stomp.newSlotScore());
        assertNotNull(stomp.occurredAt());

        ArgumentCaptor<PollVoteCastEvent> rabbitEvent = ArgumentCaptor.forClass(PollVoteCastEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eqStr(RabbitConfig.EXCHANGE),
                eqStr(RabbitConfig.ROUTING_KEY_POLL_VOTE_CAST),
                rabbitEvent.capture());
        PollVoteCastEvent event = rabbitEvent.getValue();
        assertEquals(pollId.toString(), event.getPollId());
        assertEquals(tripId.toString(), event.getTripId());
        assertEquals(slotId.toString(), event.getSlotId());
        assertEquals("YES", event.getStatus());
        assertEquals(4, event.getNewSlotScore());
    }

    private static String eqStr(String s) {
        return org.mockito.ArgumentMatchers.eq(s);
    }
}
