package com.plantogether.poll.event.publisher;

import com.plantogether.common.event.PollCreatedEvent;
import com.plantogether.poll.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PollEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishPollCreated(PollCreatedInternalEvent internal) {
        PollCreatedEvent event = PollCreatedEvent.builder()
                .pollId(internal.pollId().toString())
                .tripId(internal.tripId().toString())
                .createdByDeviceId(internal.createdByDeviceId())
                .title(internal.title())
                .slotCount(internal.slotCount())
                .createdAt(internal.createdAt())
                .build();
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ROUTING_KEY_POLL_CREATED,
                event
        );
    }

    public record PollCreatedInternalEvent(
            UUID pollId,
            UUID tripId,
            String createdByDeviceId,
            String title,
            int slotCount,
            Instant createdAt
    ) {
    }
}
