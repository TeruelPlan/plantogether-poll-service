package com.plantogether.poll.dto;

import com.plantogether.common.grpc.TripMember;
import com.plantogether.poll.domain.Poll;
import com.plantogether.poll.domain.PollResponse;
import com.plantogether.poll.service.PollScoring;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollDetailResponse {

  private UUID id;
  private UUID tripId;
  private String title;
  private String status;
  private UUID lockedSlotId;
  private UUID createdBy;
  private Instant createdAt;
  private List<SlotDetailResponse> slots;
  private List<MemberEntry> members;

  public static PollDetailResponse from(
      Poll poll, List<PollResponse> responses, List<TripMember> members) {
    Map<UUID, List<PollResponse>> responsesBySlot =
        responses.stream().collect(Collectors.groupingBy(r -> r.getPollSlot().getId()));

    List<SlotDetailResponse> slotDetails =
        poll.getSlots().stream()
            .map(
                slot -> {
                  List<PollResponse> slotResponses =
                      responsesBySlot.getOrDefault(slot.getId(), List.of());
                  int score = PollScoring.scoreForSlot(slotResponses);

                  List<VoteEntry> votes =
                      slotResponses.stream()
                          .map(
                              r ->
                                  VoteEntry.builder()
                                      .deviceId(r.getDeviceId())
                                      .status(r.getStatus().name())
                                      .build())
                          .toList();

                  return SlotDetailResponse.builder()
                      .id(slot.getId())
                      .startDate(slot.getStartDate())
                      .endDate(slot.getEndDate())
                      .slotIndex(slot.getSlotIndex())
                      .score(score)
                      .votes(votes)
                      .build();
                })
            .toList();

    List<MemberEntry> memberEntries =
        members.stream()
            .map(
                m ->
                    MemberEntry.builder()
                        .deviceId(m.deviceId())
                        .role(m.role().name())
                        .displayName(m.displayName())
                        .build())
            .toList();

    return PollDetailResponse.builder()
        .id(poll.getId())
        .tripId(poll.getTripId())
        .title(poll.getTitle())
        .status(poll.getStatus().name())
        .lockedSlotId(poll.getLockedSlotId())
        .createdBy(poll.getCreatedBy())
        .createdAt(poll.getCreatedAt())
        .slots(slotDetails)
        .members(memberEntries)
        .build();
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SlotDetailResponse {
    private UUID id;
    private LocalDate startDate;
    private LocalDate endDate;
    private int slotIndex;
    private int score;
    private List<VoteEntry> votes;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class VoteEntry {
    private UUID deviceId;
    private String status;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class MemberEntry {
    private UUID deviceId;
    private String role;
    private String displayName;
  }
}
