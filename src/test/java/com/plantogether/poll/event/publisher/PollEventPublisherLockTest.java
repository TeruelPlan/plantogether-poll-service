package com.plantogether.poll.event.publisher;

import com.plantogether.common.event.PollLockedEvent;
import com.plantogether.poll.config.RabbitConfig;
import com.plantogether.poll.event.publisher.PollEventPublisher.PollLockedInternalEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PollEventPublisherLockTest {

    @Mock RabbitTemplate rabbitTemplate;

    @InjectMocks PollEventPublisher publisher;

    @Test
    void publishPollLocked_afterCommit_sendsRabbitEvent() {
        UUID pollId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        String lockedBy = UUID.randomUUID().toString();

        PollLockedInternalEvent internal = new PollLockedInternalEvent(
                pollId, tripId, slotId,
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 8),
                lockedBy
        );

        publisher.publishPollLocked(internal);

        ArgumentCaptor<PollLockedEvent> eventCaptor = ArgumentCaptor.forClass(PollLockedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.EXCHANGE),
                eq(RabbitConfig.ROUTING_KEY_POLL_LOCKED),
                eventCaptor.capture()
        );
        PollLockedEvent event = eventCaptor.getValue();
        assertEquals(pollId.toString(), event.getPollId());
        assertEquals(tripId.toString(), event.getTripId());
        assertEquals(slotId.toString(), event.getSlotId());
        assertEquals(LocalDate.of(2026, 6, 7), event.getStartDate());
        assertEquals(LocalDate.of(2026, 6, 8), event.getEndDate());
        assertEquals(lockedBy, event.getLockedByDeviceId());
        assertNotNull(event.getOccurredAt());
    }
}
