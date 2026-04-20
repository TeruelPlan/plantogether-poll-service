package com.plantogether.poll.repository;

import com.plantogether.poll.domain.PollResponse;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PollResponseRepository extends JpaRepository<PollResponse, UUID> {

    Optional<PollResponse> findByPollSlot_IdAndDeviceId(UUID pollSlotId, UUID deviceId);

    List<PollResponse> findByPollSlot_Id(UUID pollSlotId);

    @EntityGraph(attributePaths = "pollSlot")
    List<PollResponse> findByPollSlot_Poll_Id(UUID pollId);
}
