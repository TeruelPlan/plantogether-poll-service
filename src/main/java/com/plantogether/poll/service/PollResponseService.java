package com.plantogether.poll.service;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ConflictException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.common.grpc.TripClient;
import com.plantogether.common.grpc.TripMember;
import com.plantogether.poll.domain.Poll;
import com.plantogether.poll.domain.PollResponse;
import com.plantogether.poll.domain.PollSlot;
import com.plantogether.poll.domain.PollStatus;
import com.plantogether.poll.domain.VoteStatus;
import com.plantogether.poll.dto.PollDetailResponse;
import com.plantogether.poll.dto.RespondRequest;
import com.plantogether.poll.dto.VoteResponse;
import com.plantogether.poll.event.publisher.PollRealtimeBroadcaster.PollVoteCastInternalEvent;
import com.plantogether.poll.repository.PollRepository;
import com.plantogether.poll.repository.PollResponseRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PollResponseService {

  private final PollRepository pollRepository;
  private final PollResponseRepository pollResponseRepository;
  private final PollResponseInsertHelper insertHelper;
  private final TripClient tripClient;
  private final ApplicationEventPublisher applicationEventPublisher;

  public VoteResponse respond(UUID pollId, String deviceId, RespondRequest request) {
    Poll poll =
        pollRepository
            .findById(pollId)
            .orElseThrow(() -> new ResourceNotFoundException("Poll", pollId));

    var membership = tripClient.requireMembership(poll.getTripId().toString(), deviceId);
    UUID memberUuid = UUID.fromString(membership.tripMemberId());

    if (poll.getStatus() == PollStatus.LOCKED) {
      throw new ConflictException("Poll is already locked");
    }

    PollSlot slot =
        poll.getSlots().stream()
            .filter(s -> s.getId().equals(request.getSlotId()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("PollSlot", request.getSlotId()));

    PollResponse saved =
        pollResponseRepository
            .findByPollSlot_IdAndTripMemberId(slot.getId(), memberUuid)
            .map(
                existing -> {
                  existing.setStatus(request.getStatus());
                  return pollResponseRepository.saveAndFlush(existing);
                })
            .orElseGet(() -> insertOrRecover(slot, memberUuid, request.getStatus()));

    List<PollResponse> slotResponses = pollResponseRepository.findByPollSlot_Id(slot.getId());
    int newSlotScore = PollScoring.scoreForSlot(slotResponses);

    applicationEventPublisher.publishEvent(
        new PollVoteCastInternalEvent(
            poll.getId(),
            poll.getTripId(),
            slot.getId(),
            memberUuid,
            request.getStatus(),
            newSlotScore));

    return VoteResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public PollDetailResponse getPollDetail(UUID pollId, String deviceId) {
    Poll poll =
        pollRepository
            .findById(pollId)
            .orElseThrow(() -> new ResourceNotFoundException("Poll", pollId));

    // Authorize first so non-members never see the members list.
    tripClient.requireMembership(poll.getTripId().toString(), deviceId);

    List<TripMember> members = tripClient.getTripMembers(poll.getTripId().toString());
    if (members.isEmpty()) {
      throw new AccessDeniedException("Trip has no members");
    }

    List<PollResponse> responses = pollResponseRepository.findByPollSlot_Poll_Id(pollId);
    return PollDetailResponse.from(poll, responses, members);
  }

  private PollResponse insertOrRecover(PollSlot slot, UUID memberUuid, VoteStatus status) {
    try {
      return insertHelper.insertNew(slot, memberUuid, status);
    } catch (DataIntegrityViolationException race) {
      // Concurrent insert on (poll_slot_id, trip_member_id) — re-read in the outer transaction
      // and update.
      PollResponse existing =
          pollResponseRepository
              .findByPollSlot_IdAndTripMemberId(slot.getId(), memberUuid)
              .orElseThrow(() -> race);
      existing.setStatus(status);
      return existing;
    }
  }
}
