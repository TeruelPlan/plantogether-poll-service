package com.plantogether.poll.event.publisher;

import com.plantogether.common.event.PollVoteCastEvent;
import com.plantogether.poll.config.RabbitConfig;
import com.plantogether.poll.domain.VoteStatus;
import com.plantogether.poll.event.publisher.PollRealtimeBroadcaster.PollVoteCastInternalEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PollRealtimeBroadcasterTest {

    @Mock RabbitTemplate rabbitTemplate;

    PollRealtimeBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new PollRealtimeBroadcaster(rabbitTemplate);
    }

    @Test
    void broadcastVoteCast_publishesRabbitEvent() {
        UUID pollId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        broadcaster.onVoteCast(new PollVoteCastInternalEvent(
                pollId, tripId, slotId, deviceId, VoteStatus.YES, 4));

        ArgumentCaptor<PollVoteCastEvent> rabbitEvent = ArgumentCaptor.forClass(PollVoteCastEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.EXCHANGE),
                eq(RabbitConfig.ROUTING_KEY_POLL_VOTE_CAST),
                rabbitEvent.capture());
        PollVoteCastEvent event = rabbitEvent.getValue();
        assertEquals(pollId.toString(), event.getPollId());
        assertEquals(tripId.toString(), event.getTripId());
        assertEquals(slotId.toString(), event.getSlotId());
        assertEquals(deviceId.toString(), event.getDeviceId());
        assertEquals("YES", event.getStatus());
        assertEquals(4, event.getNewSlotScore());
        assertNotNull(event.getOccurredAt());
    }
}
