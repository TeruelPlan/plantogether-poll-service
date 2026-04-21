package com.plantogether.poll.event.publisher;

import com.plantogether.common.event.PollVoteCastEvent;
import com.plantogether.poll.config.RabbitConfig;
import com.plantogether.poll.domain.VoteStatus;
import com.plantogether.poll.event.publisher.PollEventPublisher.PollLockedInternalEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PollRealtimeBroadcaster {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoteCast(PollVoteCastInternalEvent internal) {
        Instant now = Instant.now();

        PollVoteCastMessage stompMessage = new PollVoteCastMessage(
                "POLL_VOTE_CAST",
                internal.pollId().toString(),
                internal.slotId().toString(),
                internal.deviceId().toString(),
                internal.status().name(),
                internal.newSlotScore(),
                now
        );
        simpMessagingTemplate.convertAndSend(
                "/topic/trips/" + internal.tripId() + "/updates",
                stompMessage
        );

        PollVoteCastEvent rabbitEvent = PollVoteCastEvent.builder()
                .pollId(internal.pollId().toString())
                .tripId(internal.tripId().toString())
                .slotId(internal.slotId().toString())
                .deviceId(internal.deviceId().toString())
                .status(internal.status().name())
                .newSlotScore(internal.newSlotScore())
                .occurredAt(now)
                .build();
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ROUTING_KEY_POLL_VOTE_CAST,
                rabbitEvent
        );
    }

    public void broadcastPollLocked(PollLockedInternalEvent internal, Instant occurredAt) {
        PollLockedMessage message = new PollLockedMessage(
                "POLL_LOCKED",
                internal.pollId().toString(),
                internal.tripId().toString(),
                internal.slotId().toString(),
                internal.startDate().toString(),
                internal.endDate().toString(),
                occurredAt
        );
        simpMessagingTemplate.convertAndSend(
                "/topic/trips/" + internal.tripId() + "/updates",
                message
        );
    }

    public record PollLockedMessage(
            String type,
            String pollId,
            String tripId,
            String slotId,
            String startDate,
            String endDate,
            Instant occurredAt
    ) {
    }

    public record PollVoteCastInternalEvent(
            UUID pollId,
            UUID tripId,
            UUID slotId,
            UUID deviceId,
            VoteStatus status,
            int newSlotScore
    ) {
    }

    public record PollVoteCastMessage(
            String type,
            String pollId,
            String slotId,
            String deviceId,
            String status,
            int newSlotScore,
            Instant occurredAt
    ) {
    }
}
