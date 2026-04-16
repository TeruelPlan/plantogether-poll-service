package com.plantogether.poll.repository;

import com.plantogether.poll.domain.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PollRepository extends JpaRepository<Poll, UUID> {

    List<Poll> findByTripIdOrderByCreatedAtDesc(UUID tripId);
}
