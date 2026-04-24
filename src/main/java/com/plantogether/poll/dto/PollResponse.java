package com.plantogether.poll.dto;

import com.plantogether.poll.domain.Poll;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollResponse {

  private UUID id;
  private UUID tripId;
  private String title;
  private String status;
  private UUID createdBy;
  private Instant createdAt;
  private List<SlotResponse> slots;

  public static PollResponse from(Poll poll) {
    List<SlotResponse> slotResponses =
        poll.getSlots().stream()
            .map(
                s ->
                    SlotResponse.builder()
                        .id(s.getId())
                        .startDate(s.getStartDate())
                        .endDate(s.getEndDate())
                        .slotIndex(s.getSlotIndex())
                        .build())
            .toList();

    return PollResponse.builder()
        .id(poll.getId())
        .tripId(poll.getTripId())
        .title(poll.getTitle())
        .status(poll.getStatus().name())
        .createdBy(poll.getCreatedBy())
        .createdAt(poll.getCreatedAt())
        .slots(slotResponses)
        .build();
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SlotResponse {
    private UUID id;
    private LocalDate startDate;
    private LocalDate endDate;
    private int slotIndex;
  }
}
