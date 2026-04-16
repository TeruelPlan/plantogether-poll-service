package com.plantogether.poll.repository;

import com.plantogether.poll.domain.PollSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PollSlotRepository extends JpaRepository<PollSlot, UUID> {
}
