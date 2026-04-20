package com.plantogether.poll.service;

import com.plantogether.poll.domain.PollResponse;
import com.plantogether.poll.domain.PollSlot;
import com.plantogether.poll.domain.VoteStatus;
import com.plantogether.poll.repository.PollResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PollResponseInsertHelper {

    private final PollResponseRepository pollResponseRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PollResponse insertNew(PollSlot slot, UUID deviceId, VoteStatus status) {
        PollResponse vote = PollResponse.builder()
                .pollSlot(slot)
                .deviceId(deviceId)
                .status(status)
                .build();
        // saveAndFlush ensures DataIntegrityViolationException surfaces inside this method
        // rather than being deferred to commit, so the caller can recover gracefully.
        return pollResponseRepository.saveAndFlush(vote);
    }
}
