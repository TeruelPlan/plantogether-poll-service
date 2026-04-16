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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PollServiceTest {

    @Mock PollRepository pollRepository;
    @Mock TripGrpcClient tripGrpcClient;
    @Mock ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    PollService pollService;

    UUID tripId;
    String deviceId;
    CreatePollRequest request;

    @BeforeEach
    void setUp() {
        tripId = UUID.randomUUID();
        deviceId = UUID.randomUUID().toString();
        LocalDate future = LocalDate.now().plusMonths(2);
        request = CreatePollRequest.builder()
                .title("When should we leave?")
                .slots(List.of(
                        CreatePollRequest.SlotRequest.builder()
                                .startDate(future)
                                .endDate(future.plusDays(6))
                                .build(),
                        CreatePollRequest.SlotRequest.builder()
                                .startDate(future.plusDays(14))
                                .endDate(future.plusDays(20))
                                .build()
                ))
                .build();
    }

    @Test
    void createPoll_memberWithValidSlots_createsPollAndPublishesEvent() {
        when(tripGrpcClient.isMember(tripId.toString(), deviceId))
                .thenReturn(IsMemberResponse.newBuilder().setIsMember(true).setRole("PARTICIPANT").build());
        when(pollRepository.save(any(Poll.class))).thenAnswer(inv -> {
            Poll p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            p.getSlots().forEach(s -> s.setId(UUID.randomUUID()));
            return p;
        });

        PollResponse response = pollService.createPoll(tripId, deviceId, request);

        assertNotNull(response.getId());
        assertEquals(tripId, response.getTripId());
        assertEquals("When should we leave?", response.getTitle());
        assertEquals("OPEN", response.getStatus());
        assertEquals(2, response.getSlots().size());
        assertEquals(0, response.getSlots().get(0).getSlotIndex());
        assertEquals(1, response.getSlots().get(1).getSlotIndex());

        ArgumentCaptor<PollCreatedInternalEvent> eventCaptor =
                ArgumentCaptor.forClass(PollCreatedInternalEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        PollCreatedInternalEvent event = eventCaptor.getValue();
        assertEquals(tripId, event.tripId());
        assertEquals(deviceId, event.createdByDeviceId());
        assertEquals("When should we leave?", event.title());
        assertEquals(2, event.slotCount());
    }

    @Test
    void createPoll_nonMember_throwsAccessDeniedException() {
        when(tripGrpcClient.isMember(tripId.toString(), deviceId))
                .thenReturn(IsMemberResponse.newBuilder().setIsMember(false).build());

        assertThrows(AccessDeniedException.class,
                () -> pollService.createPoll(tripId, deviceId, request));
        verifyNoInteractions(applicationEventPublisher);
        verify(pollRepository, never()).save(any());
    }

    @Test
    void getPollsForTrip_member_returnsList() {
        when(tripGrpcClient.isMember(tripId.toString(), deviceId))
                .thenReturn(IsMemberResponse.newBuilder().setIsMember(true).setRole("PARTICIPANT").build());

        Poll poll = Poll.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .title("Trip poll")
                .status(PollStatus.OPEN)
                .createdBy(UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        poll.getSlots().add(PollSlot.builder()
                .id(UUID.randomUUID())
                .poll(poll)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(1))
                .slotIndex(0)
                .build());

        when(pollRepository.findByTripIdOrderByCreatedAtDesc(tripId)).thenReturn(List.of(poll));

        List<PollResponse> result = pollService.getPollsForTrip(tripId, deviceId);

        assertEquals(1, result.size());
        assertEquals("Trip poll", result.get(0).getTitle());
    }

    @Test
    void getPollsForTrip_nonMember_throwsAccessDeniedException() {
        when(tripGrpcClient.isMember(tripId.toString(), deviceId))
                .thenReturn(IsMemberResponse.newBuilder().setIsMember(false).build());

        assertThrows(AccessDeniedException.class,
                () -> pollService.getPollsForTrip(tripId, deviceId));
        verify(pollRepository, never()).findByTripIdOrderByCreatedAtDesc(any());
    }
}
