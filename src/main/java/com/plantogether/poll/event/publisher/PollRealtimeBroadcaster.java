package com.plantogether.poll.event.publisher;

import com.plantogether.common.event.PollVoteCastEvent;
import com.plantogether.poll.config.RabbitConfig;
import com.plantogether.poll.domain.VoteStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes vote-cast events to RabbitMQ after the DB transaction commits. STOMP broadcasting is
 * handled by notification-service, which consumes these events.
 */
@Component
@RequiredArgsConstructor
public class PollRealtimeBroadcaster {

  private final RabbitTemplate rabbitTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onVoteCast(PollVoteCastInternalEvent internal) {
    PollVoteCastEvent rabbitEvent =
        PollVoteCastEvent.builder()
            .pollId(internal.pollId().toString())
            .tripId(internal.tripId().toString())
            .slotId(internal.slotId().toString())
            .deviceId(internal.deviceId().toString())
            .status(internal.status().name())
            .newSlotScore(internal.newSlotScore())
            .occurredAt(Instant.now())
            .build();
    rabbitTemplate.convertAndSend(
        RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY_POLL_VOTE_CAST, rabbitEvent);
  }

  public record PollVoteCastInternalEvent(
      UUID pollId, UUID tripId, UUID slotId, UUID deviceId, VoteStatus status, int newSlotScore) {}
}
