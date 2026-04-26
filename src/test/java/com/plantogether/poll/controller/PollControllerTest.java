package com.plantogether.poll.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.common.grpc.TripClient;
import com.plantogether.common.security.SecurityAutoConfiguration;
import com.plantogether.poll.dto.PollResponse;
import com.plantogether.poll.service.PollService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PollController.class)
@Import(SecurityAutoConfiguration.class)
class PollControllerTest {

  private final UUID deviceId = UUID.randomUUID();
  private final UUID tripId = UUID.randomUUID();
  private final LocalDate futureStart1 = LocalDate.now().plusMonths(2);
  private final LocalDate futureEnd1 = futureStart1.plusDays(6);
  private final LocalDate futureStart2 = futureStart1.plusDays(14);
  private final LocalDate futureEnd2 = futureStart2.plusDays(6);

  @Autowired private MockMvc mockMvc;

  @MockBean private PollService pollService;

  @MockBean private TripClient tripClient;

  @AfterEach
  void tearDown() {
    Mockito.reset(pollService, tripClient);
  }

  private String validBody() {
    return """
    {
      "title": "When to leave?",
      "slots": [
        {"startDate": "%s", "endDate": "%s"},
        {"startDate": "%s", "endDate": "%s"}
      ]
    }
    """
        .formatted(futureStart1, futureEnd1, futureStart2, futureEnd2);
  }

  private PollResponse samplePoll() {
    return PollResponse.builder()
        .id(UUID.randomUUID())
        .tripId(tripId)
        .title("When to leave?")
        .status("OPEN")
        .createdBy(deviceId)
        .createdAt(Instant.now())
        .slots(
            List.of(
                PollResponse.SlotResponse.builder()
                    .id(UUID.randomUUID())
                    .startDate(futureStart1)
                    .endDate(futureEnd1)
                    .slotIndex(0)
                    .build(),
                PollResponse.SlotResponse.builder()
                    .id(UUID.randomUUID())
                    .startDate(futureStart2)
                    .endDate(futureEnd2)
                    .slotIndex(1)
                    .build()))
        .build();
  }

  @Test
  void createPoll_returns201_withValidBody() throws Exception {
    when(pollService.createPoll(eq(tripId), eq(deviceId.toString()), any()))
        .thenReturn(samplePoll());

    mockMvc
        .perform(
            post("/api/v1/trips/{tripId}/polls", tripId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content(validBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("When to leave?"))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.slots.length()").value(2));
  }

  @Test
  void createPoll_returns403_forNonMember() throws Exception {
    when(pollService.createPoll(eq(tripId), eq(deviceId.toString()), any()))
        .thenThrow(new AccessDeniedException("Not a member"));

    mockMvc
        .perform(
            post("/api/v1/trips/{tripId}/polls", tripId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content(validBody()))
        .andExpect(status().isForbidden());
  }

  @Test
  void createPoll_returns400_withOneSlot() throws Exception {
    String body =
        """
        {
          "title": "One slot only",
          "slots": [
            {"startDate": "%s", "endDate": "%s"}
          ]
        }
        """
            .formatted(futureStart1, futureEnd1);

    mockMvc
        .perform(
            post("/api/v1/trips/{tripId}/polls", tripId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createPoll_returns400_withEndDateBeforeStartDate() throws Exception {
    String body =
        """
        {
          "title": "Bad range",
          "slots": [
            {"startDate": "%s", "endDate": "%s"},
            {"startDate": "%s", "endDate": "%s"}
          ]
        }
        """
            .formatted(futureEnd1, futureStart1, futureStart2, futureEnd2);

    mockMvc
        .perform(
            post("/api/v1/trips/{tripId}/polls", tripId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createPoll_returns400_withPastStartDate() throws Exception {
    String body =
        """
        {
          "title": "Past dates",
          "slots": [
            {"startDate": "2020-01-01", "endDate": "2020-01-07"},
            {"startDate": "%s", "endDate": "%s"}
          ]
        }
        """
            .formatted(futureStart2, futureEnd2);

    mockMvc
        .perform(
            post("/api/v1/trips/{tripId}/polls", tripId)
                .header("X-Device-Id", deviceId.toString())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getPolls_returns200_withPollList() throws Exception {
    when(pollService.getPollsForTrip(eq(tripId), eq(deviceId.toString())))
        .thenReturn(List.of(samplePoll()));

    mockMvc
        .perform(
            get("/api/v1/trips/{tripId}/polls", tripId).header("X-Device-Id", deviceId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].title").value("When to leave?"));
  }
}
