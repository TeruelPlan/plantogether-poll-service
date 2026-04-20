package com.plantogether.poll.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ConflictException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.poll.domain.Poll;
import com.plantogether.poll.domain.PollSlot;
import com.plantogether.poll.domain.PollStatus;
import com.plantogether.poll.dto.PollDetailResponse;
import com.plantogether.poll.event.publisher.PollEventPublisher.PollLockedInternalEvent;
import com.plantogether.poll.grpc.client.TripGrpcClient;
import com.plantogether.poll.repository.PollRepository;
import com.plantogether.poll.repository.PollResponseRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PollServiceLockTest {

    @Mock PollRepository pollRepository;
    @Mock PollResponseRepository pollResponseRepository;
    @Mock TripGrpcClient tripGrpcClient;
    @Mock ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks PollService pollService;

    UUID tripId;
    UUID pollId;
    UUID slotId;
    String organizerDeviceId;
    Poll poll;
    PollSlot slot;

    @BeforeEach
    void setUp() {
        tripId = UUID.randomUUID();
        pollId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        organizerDeviceId = UUID.randomUUID().toString();
        poll = Poll.builder()
                .id(pollId)
                .tripId(tripId)
                .title("When?")
                .status(PollStatus.OPEN)
                .createdBy(UUID.fromString(organizerDeviceId))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        slot = PollSlot.builder()
                .id(slotId)
                .poll(poll)
                .startDate(LocalDate.of(2026, 6, 7))
                .endDate(LocalDate.of(2026, 6, 8))
                .slotIndex(0)
                .build();
        poll.getSlots().add(slot);
    }

    @Test
    void lockPoll_organizer_updatesStatusAndPublishesEvent() {
        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        when(tripGrpcClient.isMember(tripId.toString(), organizerDeviceId))
                .thenReturn(IsMemberResponse.newBuilder().setIsMember(true).setRole("ORGANIZER").build());
        when(tripGrpcClient.getTripMembers(tripId.toString())).thenReturn(List.of());
        when(pollResponseRepository.findByPollSlot_Poll_Id(pollId)).thenReturn(List.of());

        PollDetailResponse result = pollService.lockPoll(pollId, organizerDeviceId, slotId);

        assertEquals("LOCKED", result.getStatus());
        assertEquals(slotId, result.getLockedSlotId());
        assertEquals(PollStatus.LOCKED, poll.getStatus());
        assertEquals(slotId, poll.getLockedSlotId());
        verify(pollRepository).saveAndFlush(poll);

        ArgumentCaptor<PollLockedInternalEvent> captor =
                ArgumentCaptor.forClass(PollLockedInternalEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        PollLockedInternalEvent evt = captor.getValue();
        assertEquals(pollId, evt.pollId());
        assertEquals(tripId, evt.tripId());
        assertEquals(slotId, evt.slotId());
        assertEquals(LocalDate.of(2026, 6, 7), evt.startDate());
        assertEquals(LocalDate.of(2026, 6, 8), evt.endDate());
        assertEquals(organizerDeviceId, evt.lockedByDeviceId());
    }

    @Test
    void lockPoll_participant_throwsAccessDenied() {
        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        when(tripGrpcClient.isMember(tripId.toString(), organizerDeviceId))
                .thenReturn(IsMemberResponse.newBuilder().setIsMember(true).setRole("PARTICIPANT").build());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> pollService.lockPoll(pollId, organizerDeviceId, slotId));
        assertTrue(ex.getMessage().contains("organizer"));
        verify(pollRepository, never()).saveAndFlush(any());
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void lockPoll_nonMember_throwsAccessDenied() {
        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        when(tripGrpcClient.isMember(tripId.toString(), organizerDeviceId))
                .thenReturn(IsMemberResponse.newBuilder().setIsMember(false).build());

        assertThrows(AccessDeniedException.class,
                () -> pollService.lockPoll(pollId, organizerDeviceId, slotId));
        verify(pollRepository, never()).saveAndFlush(any());
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void lockPoll_alreadyLocked_throwsConflict() {
        poll.setStatus(PollStatus.LOCKED);
        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        when(tripGrpcClient.isMember(tripId.toString(), organizerDeviceId))
                .thenReturn(IsMemberResponse.newBuilder().setIsMember(true).setRole("ORGANIZER").build());

        assertThrows(ConflictException.class,
                () -> pollService.lockPoll(pollId, organizerDeviceId, slotId));
        verify(pollRepository, never()).saveAndFlush(any());
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void lockPoll_slotNotInPoll_throwsIllegalArgument() {
        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        when(tripGrpcClient.isMember(tripId.toString(), organizerDeviceId))
                .thenReturn(IsMemberResponse.newBuilder().setIsMember(true).setRole("ORGANIZER").build());

        UUID unknownSlotId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> pollService.lockPoll(pollId, organizerDeviceId, unknownSlotId));
        verify(pollRepository, never()).saveAndFlush(any());
    }

    @Test
    void lockPoll_pollNotFound_throwsResourceNotFound() {
        when(pollRepository.findById(pollId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> pollService.lockPoll(pollId, organizerDeviceId, slotId));
        verifyNoInteractions(tripGrpcClient);
    }
}
