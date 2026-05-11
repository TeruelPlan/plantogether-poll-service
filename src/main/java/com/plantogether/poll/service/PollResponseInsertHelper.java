package com.plantogether.poll.service;

import com.plantogether.poll.domain.PollResponse;
import com.plantogether.poll.domain.PollSlot;
import com.plantogether.poll.domain.VoteStatus;
import com.plantogether.poll.repository.PollResponseRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PollResponseInsertHelper {

  private final PollResponseRepository pollResponseRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public PollResponse insertNew(PollSlot slot, UUID tripMemberId, VoteStatus status) {
    PollResponse vote =
        PollResponse.builder().pollSlot(slot).tripMemberId(tripMemberId).status(status).build();
    return pollResponseRepository.saveAndFlush(vote);
  }
}
