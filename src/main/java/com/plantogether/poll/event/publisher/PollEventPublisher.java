package com.plantogether.poll.event.publisher;

import com.plantogether.common.event.PollCreatedEvent;
import com.plantogether.common.event.PollLockedEvent;
import com.plantogether.poll.config.RabbitConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes poll lifecycle events to RabbitMQ. Real-time STOMP broadcasting is handled by
 * notification-service, which consumes these events.
 */
@Component
@RequiredArgsConstructor
public class PollEventPublisher {

  private final RabbitTemplate rabbitTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publishPollCreated(PollCreatedInternalEvent internal) {
    PollCreatedEvent event =
        PollCreatedEvent.builder()
            .pollId(internal.pollId().toString())
            .tripId(internal.tripId().toString())
            .createdByDeviceId(internal.createdByDeviceId())
            .title(internal.title())
            .slotCount(internal.slotCount())
            .createdAt(internal.createdAt())
            .build();
    rabbitTemplate.convertAndSend(
        RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY_POLL_CREATED, event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publishPollLocked(PollLockedInternalEvent internal) {
    PollLockedEvent event =
        PollLockedEvent.builder()
            .pollId(internal.pollId().toString())
            .tripId(internal.tripId().toString())
            .slotId(internal.slotId().toString())
            .startDate(internal.startDate())
            .endDate(internal.endDate())
            .lockedByDeviceId(internal.lockedByDeviceId())
            .occurredAt(Instant.now())
            .build();
    rabbitTemplate.convertAndSend(
        RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY_POLL_LOCKED, event);
  }

  public record PollCreatedInternalEvent(
      UUID pollId,
      UUID tripId,
      String createdByDeviceId,
      String title,
      int slotCount,
      Instant createdAt) {}

  public record PollLockedInternalEvent(
      UUID pollId,
      UUID tripId,
      UUID slotId,
      LocalDate startDate,
      LocalDate endDate,
      String lockedByDeviceId) {}
}
