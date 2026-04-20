package com.plantogether.poll.controller;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.exception.ConflictException;
import com.plantogether.common.exception.ResourceNotFoundException;
import com.plantogether.common.security.SecurityAutoConfiguration;
import com.plantogether.poll.dto.PollDetailResponse;
import com.plantogether.poll.dto.VoteResponse;
import com.plantogether.poll.grpc.client.TripGrpcClient;
import com.plantogether.poll.service.PollResponseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PollDetailController.class)
@Import(SecurityAutoConfiguration.class)
class PollDetailControllerTest {

    private final UUID deviceId = UUID.randomUUID();
    private final UUID pollId = UUID.randomUUID();
    private final UUID slotId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PollResponseService pollResponseService;

    @MockBean
    private TripGrpcClient tripGrpcClient;

    @AfterEach
    void tearDown() {
        Mockito.reset(pollResponseService, tripGrpcClient);
    }

    private String validBody() {
        return """
                {
                  "slotId": "%s",
                  "status": "YES"
                }
                """.formatted(slotId);
    }

    private VoteResponse sampleVote() {
        return VoteResponse.builder()
                .slotId(slotId)
                .status("YES")
                .deviceId(deviceId)
                .build();
    }

    private PollDetailResponse sampleDetail() {
        PollDetailResponse.SlotDetailResponse slot = PollDetailResponse.SlotDetailResponse.builder()
                .id(slotId)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .slotIndex(0)
                .score(0)
                .votes(List.of())
                .build();
        List<PollDetailResponse.MemberEntry> members = List.of(
                PollDetailResponse.MemberEntry.builder()
                        .deviceId(UUID.randomUUID()).role("ORGANIZER").displayName("Alice").build(),
                PollDetailResponse.MemberEntry.builder()
                        .deviceId(UUID.randomUUID()).role("PARTICIPANT").displayName("Bob").build(),
                PollDetailResponse.MemberEntry.builder()
                        .deviceId(UUID.randomUUID()).role("PARTICIPANT").displayName("Carol").build()
        );
        return PollDetailResponse.builder()
                .id(pollId)
                .tripId(UUID.randomUUID())
                .title("When to leave?")
                .status("OPEN")
                .lockedSlotId(null)
                .createdBy(UUID.randomUUID())
                .createdAt(Instant.now())
                .slots(List.of(slot))
                .members(members)
                .build();
    }

    @Test
    void respond_returns200_andDelegatesService() throws Exception {
        when(pollResponseService.respond(eq(pollId), eq(deviceId.toString()), any()))
                .thenReturn(sampleVote());

        mockMvc.perform(put("/api/v1/polls/{pollId}/respond", pollId)
                        .header("X-Device-Id", deviceId.toString())
                        .contentType("application/json")
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value(slotId.toString()))
                .andExpect(jsonPath("$.status").value("YES"));
    }

    @Test
    void respond_returns400_whenStatusInvalid() throws Exception {
        String body = """
                {
                  "slotId": "%s",
                  "status": "UNKNOWN"
                }
                """.formatted(slotId);

        mockMvc.perform(put("/api/v1/polls/{pollId}/respond", pollId)
                        .header("X-Device-Id", deviceId.toString())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void respond_returns403_whenServiceThrowsAccessDenied() throws Exception {
        when(pollResponseService.respond(eq(pollId), eq(deviceId.toString()), any()))
                .thenThrow(new AccessDeniedException("Not a member"));

        mockMvc.perform(put("/api/v1/polls/{pollId}/respond", pollId)
                        .header("X-Device-Id", deviceId.toString())
                        .contentType("application/json")
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void respond_returns409_whenServiceThrowsConflict() throws Exception {
        when(pollResponseService.respond(eq(pollId), eq(deviceId.toString()), any()))
                .thenThrow(new ConflictException("Poll is already locked"));

        mockMvc.perform(put("/api/v1/polls/{pollId}/respond", pollId)
                        .header("X-Device-Id", deviceId.toString())
                        .contentType("application/json")
                        .content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Poll is already locked"));
    }

    @Test
    void respond_returns404_whenServiceThrowsResourceNotFound() throws Exception {
        when(pollResponseService.respond(eq(pollId), eq(deviceId.toString()), any()))
                .thenThrow(new ResourceNotFoundException("PollSlot", slotId));

        mockMvc.perform(put("/api/v1/polls/{pollId}/respond", pollId)
                        .header("X-Device-Id", deviceId.toString())
                        .contentType("application/json")
                        .content(validBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPollDetail_returns200_withDetailDto() throws Exception {
        when(pollResponseService.getPollDetail(eq(pollId), eq(deviceId.toString())))
                .thenReturn(sampleDetail());

        mockMvc.perform(get("/api/v1/polls/{pollId}", pollId)
                        .header("X-Device-Id", deviceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pollId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.slots.length()").value(1))
                .andExpect(jsonPath("$.slots[0].score").value(0))
                .andExpect(jsonPath("$.members.length()").value(3))
                .andExpect(jsonPath("$.members[0].displayName").value("Alice"));
    }
}
