package com.plantogether.poll.controller;

import com.plantogether.poll.dto.CreatePollRequest;
import com.plantogether.poll.dto.PollResponse;
import com.plantogether.poll.service.PollService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/polls")
@RequiredArgsConstructor
public class PollController {

  private final PollService pollService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PollResponse createPoll(
      Authentication authentication,
      @PathVariable UUID tripId,
      @Valid @RequestBody CreatePollRequest request) {
    String deviceId = authentication.getName();
    return pollService.createPoll(tripId, deviceId, request);
  }

  @GetMapping
  public List<PollResponse> getPollsForTrip(
      Authentication authentication, @PathVariable UUID tripId) {
    String deviceId = authentication.getName();
    return pollService.getPollsForTrip(tripId, deviceId);
  }
}
