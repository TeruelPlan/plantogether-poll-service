package com.plantogether.poll.controller;

import com.plantogether.poll.dto.LockPollRequest;
import com.plantogether.poll.dto.PollDetailResponse;
import com.plantogether.poll.dto.RespondRequest;
import com.plantogether.poll.dto.VoteResponse;
import com.plantogether.poll.service.PollResponseService;
import com.plantogether.poll.service.PollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/polls/{pollId}")
@RequiredArgsConstructor
public class PollDetailController {

    private final PollResponseService pollResponseService;
    private final PollService pollService;

    @GetMapping
    public PollDetailResponse getPollDetail(Authentication authentication,
                                            @PathVariable UUID pollId) {
        String deviceId = authentication.getName();
        return pollResponseService.getPollDetail(pollId, deviceId);
    }

    @PutMapping("/respond")
    public VoteResponse respond(Authentication authentication,
                                @PathVariable UUID pollId,
                                @Valid @RequestBody RespondRequest request) {
        String deviceId = authentication.getName();
        return pollResponseService.respond(pollId, deviceId, request);
    }

    @PutMapping("/lock")
    public PollDetailResponse lockPoll(Authentication authentication,
                                       @PathVariable UUID pollId,
                                       @Valid @RequestBody LockPollRequest request) {
        String deviceId = authentication.getName();
        return pollService.lockPoll(pollId, deviceId, request.getSlotId());
    }
}
