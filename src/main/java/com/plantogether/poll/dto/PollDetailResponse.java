package com.plantogether.poll.dto;

import com.plantogether.poll.domain.Poll;
import com.plantogether.poll.domain.PollResponse;
import com.plantogether.poll.domain.VoteStatus;
import com.plantogether.trip.grpc.TripMemberProto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    public static PollDetailResponse from(Poll poll, List<PollResponse> responses, List<TripMemberProto> members) {
        Map<UUID, List<PollResponse>> responsesBySlot = responses.stream()
                .collect(Collectors.groupingBy(r -> r.getPollSlot().getId()));

        List<SlotDetailResponse> slotDetails = poll.getSlots().stream()
                .map(slot -> {
                    List<PollResponse> slotResponses = responsesBySlot.getOrDefault(slot.getId(), List.of());
                    int yesCount = (int) slotResponses.stream().filter(r -> r.getStatus() == VoteStatus.YES).count();
                    int maybeCount = (int) slotResponses.stream().filter(r -> r.getStatus() == VoteStatus.MAYBE).count();
                    int score = (yesCount * 2) + maybeCount;

                    List<VoteEntry> votes = slotResponses.stream()
                            .map(r -> VoteEntry.builder()
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

        List<MemberEntry> memberEntries = members.stream()
                .map(m -> MemberEntry.builder()
                        .deviceId(UUID.fromString(m.getDeviceId()))
                        .role(m.getRole())
                        .displayName(m.getDisplayName())
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
