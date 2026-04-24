package com.plantogether.poll.repository;

import com.plantogether.poll.domain.PollSlot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PollSlotRepository extends JpaRepository<PollSlot, UUID> {}
