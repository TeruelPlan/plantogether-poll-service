package com.plantogether.poll.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ConflictException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.poll.domain.Poll;
import com.plantogether.poll.domain.PollResponse;
import com.plantogether.poll.domain.PollSlot;
import com.plantogether.poll.domain.PollStatus;
import com.plantogether.poll.domain.VoteStatus;
import com.plantogether.poll.dto.PollDetailResponse;
import com.plantogether.poll.dto.RespondRequest;
import com.plantogether.poll.dto.VoteResponse;
import com.plantogether.poll.event.publisher.PollRealtimeBroadcaster.PollVoteCastInternalEvent;
import com.plantogether.poll.grpc.client.TripGrpcClient;
import com.plantogether.poll.repository.PollRepository;
import com.plantogether.poll.repository.PollResponseRepository;
import com.plantogether.trip.grpc.IsMemberResponse;
import com.plantogether.trip.grpc.TripMemberProto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PollResponseServiceTest {

    @Mock PollRepository pollRepository;
    @Mock PollResponseRepository pollResponseRepository;
    @Mock PollResponseInsertHelper insertHelper;
    @Mock TripGrpcClient tripGrpcClient;
    @Mock ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    PollResponseService service;

    UUID pollId;
    UUID tripId;
    UUID slotAId;
    UUID slotBId;
    String deviceId;
    UUID deviceUuid;
    Poll poll;
    PollSlot slotA;
    PollSlot slotB;

    @BeforeEach
    void setUp() {
        pollId = UUID.randomUUID();
        tripId = UUID.randomUUID();
        slotAId = UUID.randomUUID();
        slotBId = UUID.randomUUID();
        deviceUuid = UUID.randomUUID();
        deviceId = deviceUuid.toString();

        slotA = PollSlot.builder()
                .id(slotAId)
                .startDate(LocalDate.now().plusMonths(1))
                .endDate(LocalDate.now().plusMonths(1).plusDays(2))
                .slotIndex(0)
                .build();
        slotB = PollSlot.builder()
                .id(slotBId)
                .startDate(LocalDate.now().plusMonths(2))
                .endDate(LocalDate.now().plusMonths(2).plusDays(2))
                .slotIndex(1)
                .build();

        poll = Poll.builder()
                .id(pollId)
                .tripId(tripId)
                .title("When?")
                .status(PollStatus.OPEN)
                .createdBy(UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        slotA.setPoll(poll);
        slotB.setPoll(poll);
        poll.getSlots().add(slotA);
        poll.getSlots().add(slotB);
    }

    private void stubMember(boolean member) {
        when(tripGrpcClient.isMember(tripId.toString(), deviceId))
                .thenReturn(IsMemberResponse.newBuilder().setIsMember(member).setRole(member ? "PARTICIPANT" : "").build());
    }

    @Test
    void respond_newVote_member_savesAndReturns() {
        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        stubMember(true);
        when(pollResponseRepository.findByPollSlot_IdAndDeviceId(slotAId, deviceUuid))
                .thenReturn(Optional.empty());
        when(insertHelper.insertNew(slotA, deviceUuid, VoteStatus.YES)).thenAnswer(inv -> {
            PollResponse pr = PollResponse.builder()
                    .id(UUID.randomUUID())
                    .pollSlot(slotA)
                    .deviceId(deviceUuid)
                    .status(VoteStatus.YES)
                    .build();
            return pr;
        });
        when(pollResponseRepository.findByPollSlot_Id(slotAId)).thenReturn(List.of(
                PollResponse.builder().id(UUID.randomUUID()).pollSlot(slotA).deviceId(deviceUuid).status(VoteStatus.YES).build()
        ));

        VoteResponse result = service.respond(pollId, deviceId,
                RespondRequest.builder().slotId(slotAId).status(VoteStatus.YES).build());

        assertEquals(slotAId, result.getSlotId());
        assertEquals("YES", result.getStatus());
        assertEquals(deviceUuid, result.getDeviceId());

        verify(insertHelper).insertNew(slotA, deviceUuid, VoteStatus.YES);
    }

    @Test
    void respond_existingVote_member_overwritesStatus() {
        PollResponse existing = PollResponse.builder()
                .id(UUID.randomUUID())
                .pollSlot(slotA)
                .deviceId(deviceUuid)
                .status(VoteStatus.YES)
                .build();

        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        stubMember(true);
        when(pollResponseRepository.findByPollSlot_IdAndDeviceId(slotAId, deviceUuid))
                .thenReturn(Optional.of(existing));
        when(pollResponseRepository.findByPollSlot_Id(slotAId)).thenReturn(List.of(existing));

        VoteResponse result = service.respond(pollId, deviceId,
                RespondRequest.builder().slotId(slotAId).status(VoteStatus.MAYBE).build());

        assertEquals(VoteStatus.MAYBE, existing.getStatus());
        assertEquals("MAYBE", result.getStatus());
        verifyNoInteractions(insertHelper);
        verify(pollResponseRepository, never()).save(any());
    }

    @Test
    void respond_lockedPoll_throwsConflictException() {
        poll.setStatus(PollStatus.LOCKED);
        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        stubMember(true);

        assertThrows(ConflictException.class, () -> service.respond(pollId, deviceId,
                RespondRequest.builder().slotId(slotAId).status(VoteStatus.YES).build()));

        verifyNoInteractions(insertHelper);
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void respond_nonMember_throwsAccessDeniedException() {
        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        stubMember(false);

        assertThrows(AccessDeniedException.class, () -> service.respond(pollId, deviceId,
                RespondRequest.builder().slotId(slotAId).status(VoteStatus.YES).build()));

        verifyNoInteractions(insertHelper);
        verify(pollResponseRepository, never()).findByPollSlot_IdAndDeviceId(any(), any());
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void respond_unknownSlotId_throwsResourceNotFoundException() {
        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        stubMember(true);
        UUID unknownSlot = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class, () -> service.respond(pollId, deviceId,
                RespondRequest.builder().slotId(unknownSlot).status(VoteStatus.YES).build()));

        verifyNoInteractions(insertHelper);
    }

    @Test
    void respond_unknownPollId_throwsResourceNotFoundException() {
        when(pollRepository.findById(pollId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.respond(pollId, deviceId,
                RespondRequest.builder().slotId(slotAId).status(VoteStatus.YES).build()));

        verifyNoInteractions(tripGrpcClient);
        verifyNoInteractions(insertHelper);
    }

    @Test
    void respond_afterSave_publishesInternalEvent() {
        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        stubMember(true);
        when(pollResponseRepository.findByPollSlot_IdAndDeviceId(slotAId, deviceUuid)).thenReturn(Optional.empty());
        PollResponse inserted = PollResponse.builder()
                .id(UUID.randomUUID()).pollSlot(slotA).deviceId(deviceUuid).status(VoteStatus.YES).build();
        when(insertHelper.insertNew(slotA, deviceUuid, VoteStatus.YES)).thenReturn(inserted);
        when(pollResponseRepository.findByPollSlot_Id(slotAId)).thenReturn(List.of(inserted));

        service.respond(pollId, deviceId,
                RespondRequest.builder().slotId(slotAId).status(VoteStatus.YES).build());

        ArgumentCaptor<PollVoteCastInternalEvent> captor = ArgumentCaptor.forClass(PollVoteCastInternalEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        PollVoteCastInternalEvent event = captor.getValue();
        assertEquals(pollId, event.pollId());
        assertEquals(tripId, event.tripId());
        assertEquals(slotAId, event.slotId());
        assertEquals(deviceUuid, event.deviceId());
        assertEquals(VoteStatus.YES, event.status());
        assertEquals(2, event.newSlotScore(), "1 YES × 2 = 2");
    }

    @Test
    void respond_concurrentInsert_fallbackUpdates() {
        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        stubMember(true);
        when(pollResponseRepository.findByPollSlot_IdAndDeviceId(slotAId, deviceUuid))
                .thenReturn(Optional.empty())  // first lookup: nothing
                .thenReturn(Optional.of(PollResponse.builder()
                        .id(UUID.randomUUID()).pollSlot(slotA).deviceId(deviceUuid).status(VoteStatus.MAYBE).build()));  // race re-lookup
        when(insertHelper.insertNew(slotA, deviceUuid, VoteStatus.YES))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(pollResponseRepository.save(any(PollResponse.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pollResponseRepository.findByPollSlot_Id(slotAId)).thenReturn(List.of(
                PollResponse.builder().id(UUID.randomUUID()).pollSlot(slotA).deviceId(deviceUuid).status(VoteStatus.YES).build()
        ));

        VoteResponse result = service.respond(pollId, deviceId,
                RespondRequest.builder().slotId(slotAId).status(VoteStatus.YES).build());

        assertEquals("YES", result.getStatus());
        verify(pollResponseRepository, times(2)).findByPollSlot_IdAndDeviceId(slotAId, deviceUuid);
        verify(applicationEventPublisher).publishEvent(any(PollVoteCastInternalEvent.class));
    }

    @Test
    void getPollDetail_member_returnsAggregatedScore() {
        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        stubMember(true);
        when(tripGrpcClient.getTripMembers(tripId.toString())).thenReturn(List.of(
                TripMemberProto.newBuilder().setDeviceId(UUID.randomUUID().toString()).setRole("ORGANIZER").setDisplayName("Alice").build()
        ));

        UUID voterA = UUID.randomUUID();
        UUID voterB = UUID.randomUUID();
        UUID voterC = UUID.randomUUID();
        List<PollResponse> responses = List.of(
                PollResponse.builder().id(UUID.randomUUID()).pollSlot(slotA).deviceId(voterA).status(VoteStatus.YES).build(),
                PollResponse.builder().id(UUID.randomUUID()).pollSlot(slotA).deviceId(voterB).status(VoteStatus.YES).build(),
                PollResponse.builder().id(UUID.randomUUID()).pollSlot(slotA).deviceId(voterC).status(VoteStatus.MAYBE).build()
        );
        when(pollResponseRepository.findByPollSlot_Poll_Id(pollId)).thenReturn(responses);

        PollDetailResponse detail = service.getPollDetail(pollId, deviceId);

        assertEquals(pollId, detail.getId());
        assertEquals(2, detail.getSlots().size());
        PollDetailResponse.SlotDetailResponse slotADetail = detail.getSlots().get(0);
        assertEquals(slotAId, slotADetail.getId());
        assertEquals(5, slotADetail.getScore(), "2 YES × 2 + 1 MAYBE × 1 = 5");
        assertEquals(3, slotADetail.getVotes().size());
        assertEquals(0, detail.getSlots().get(1).getScore());
        assertEquals(1, detail.getMembers().size());
        assertEquals("Alice", detail.getMembers().get(0).getDisplayName());
        verify(tripGrpcClient, times(1)).isMember(tripId.toString(), deviceId);
    }

    @Test
    void getPollDetail_nonMember_throwsAccessDeniedException() {
        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        stubMember(false);

        assertThrows(AccessDeniedException.class, () -> service.getPollDetail(pollId, deviceId));
        verify(pollResponseRepository, never()).findByPollSlot_Poll_Id(any());
    }
}
