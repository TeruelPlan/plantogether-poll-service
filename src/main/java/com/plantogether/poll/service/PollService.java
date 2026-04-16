package com.plantogether.poll.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.poll.domain.Poll;
import com.plantogether.poll.domain.PollSlot;
import com.plantogether.poll.domain.PollStatus;
import com.plantogether.poll.dto.CreatePollRequest;
import com.plantogether.poll.dto.PollResponse;
import com.plantogether.poll.event.publisher.PollEventPublisher.PollCreatedInternalEvent;
import com.plantogether.poll.grpc.client.TripGrpcClient;
import com.plantogether.poll.repository.PollRepository;
import com.plantogether.trip.grpc.IsMemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional
public class PollService {

    private final PollRepository pollRepository;
    private final TripGrpcClient tripGrpcClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    public PollResponse createPoll(UUID tripId, String deviceId, CreatePollRequest request) {
        IsMemberResponse membership = tripGrpcClient.isMember(tripId.toString(), deviceId);
        if (!membership.getIsMember()) {
            throw new AccessDeniedException("Device is not a member of this trip");
        }

        Instant now = Instant.now();
        Poll poll = Poll.builder()
                .tripId(tripId)
                .title(request.getTitle())
                .status(PollStatus.OPEN)
                .createdBy(UUID.fromString(deviceId))
                .createdAt(now)
                .updatedAt(now)
                .build();

        List<CreatePollRequest.SlotRequest> slotRequests = request.getSlots();
        List<PollSlot> slots = IntStream.range(0, slotRequests.size())
                .mapToObj(i -> {
                    CreatePollRequest.SlotRequest slotRequest = slotRequests.get(i);
                    return PollSlot.builder()
                            .poll(poll)
                            .startDate(slotRequest.getStartDate())
                            .endDate(slotRequest.getEndDate())
                            .slotIndex(i)
                            .build();
                })
                .toList();
        poll.getSlots().addAll(slots);

        Poll saved = pollRepository.save(poll);

        applicationEventPublisher.publishEvent(new PollCreatedInternalEvent(
                saved.getId(),
                tripId,
                deviceId,
                saved.getTitle(),
                saved.getSlots().size(),
                saved.getCreatedAt()
        ));

        return PollResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PollResponse> getPollsForTrip(UUID tripId, String deviceId) {
        IsMemberResponse membership = tripGrpcClient.isMember(tripId.toString(), deviceId);
        if (!membership.getIsMember()) {
            throw new AccessDeniedException("Device is not a member of this trip");
        }

        return pollRepository.findByTripIdOrderByCreatedAtDesc(tripId).stream()
                .map(PollResponse::from)
                .toList();
    }
}
