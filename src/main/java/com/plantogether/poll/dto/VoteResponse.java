package com.plantogether.poll.dto;

import com.plantogether.poll.domain.PollResponse;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteResponse {

  private UUID slotId;
  private String status;
  private UUID tripMemberId;

  public static VoteResponse from(PollResponse response) {
    return VoteResponse.builder()
        .slotId(response.getPollSlot().getId())
        .status(response.getStatus().name())
        .tripMemberId(response.getTripMemberId())
        .build();
  }
}
