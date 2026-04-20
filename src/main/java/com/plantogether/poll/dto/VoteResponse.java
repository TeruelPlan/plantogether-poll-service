package com.plantogether.poll.dto;

import com.plantogether.poll.domain.PollResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteResponse {

    private UUID slotId;
    private String status;
    private UUID deviceId;

    public static VoteResponse from(PollResponse response) {
        return VoteResponse.builder()
                .slotId(response.getPollSlot().getId())
                .status(response.getStatus().name())
                .deviceId(response.getDeviceId())
                .build();
    }
}
