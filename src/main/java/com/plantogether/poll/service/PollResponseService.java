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

    tripClient.requireMembership(poll.getTripId().toString(), deviceId);

    if (poll.getStatus() == PollStatus.LOCKED) {
      throw new ConflictException("Poll is already locked");
    }

    PollSlot slot =
        poll.getSlots().stream()
            .filter(s -> s.getId().equals(request.getSlotId()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("PollSlot", request.getSlotId()));

    UUID deviceUuid = UUID.fromString(deviceId);

    PollResponse saved =
        pollResponseRepository
            .findByPollSlot_IdAndDeviceId(slot.getId(), deviceUuid)
            .map(
                existing -> {
                  existing.setStatus(request.getStatus());
                  return pollResponseRepository.saveAndFlush(existing);
                })
            .orElseGet(() -> insertOrRecover(slot, deviceUuid, request.getStatus()));

    List<PollResponse> slotResponses = pollResponseRepository.findByPollSlot_Id(slot.getId());
    int newSlotScore = PollScoring.scoreForSlot(slotResponses);

    applicationEventPublisher.publishEvent(
        new PollVoteCastInternalEvent(
            poll.getId(),
            poll.getTripId(),
            slot.getId(),
            deviceUuid,
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

    // Surface gRPC failure as 5xx to the caller — the matrix is meaningless without member columns.
    // The members list also gates authorization: self-membership is confirmed by presence in the
    // list,
    // saving one round-trip compared to a separate IsMember call.
    List<TripMember> members = tripClient.getTripMembers(poll.getTripId().toString());
    boolean isMember = members.stream().anyMatch(m -> deviceId.equals(m.deviceId().toString()));
    if (!isMember) {
      throw new AccessDeniedException("Device is not a member of this trip");
    }

    List<PollResponse> responses = pollResponseRepository.findByPollSlot_Poll_Id(pollId);
    return PollDetailResponse.from(poll, responses, members);
  }

  private PollResponse insertOrRecover(PollSlot slot, UUID deviceUuid, VoteStatus status) {
    try {
      return insertHelper.insertNew(slot, deviceUuid, status);
    } catch (DataIntegrityViolationException race) {
      // Concurrent insert on (poll_slot_id, device_id) — re-read in the outer transaction and
      // update.
      PollResponse existing =
          pollResponseRepository
              .findByPollSlot_IdAndDeviceId(slot.getId(), deviceUuid)
              .orElseThrow(() -> race);
      existing.setStatus(status);
      return existing;
    }
  }
}
