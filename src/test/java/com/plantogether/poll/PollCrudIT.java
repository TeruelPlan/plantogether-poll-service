package com.plantogether.poll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.plantogether.common.grpc.Role;
import com.plantogether.common.grpc.TripMembership;
import com.plantogether.poll.dto.CreatePollRequest;
import com.plantogether.poll.dto.PollResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class PollCrudIT extends AbstractIntegrationTest {

  @BeforeEach
  void stubTripClient() {
    when(tripClient.requireMembership(anyString(), anyString()))
        .thenReturn(new TripMembership(true, Role.ORGANIZER));
    when(tripClient.isMember(anyString(), anyString())).thenReturn(true);
  }

  private HttpHeaders headers(UUID deviceId) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    h.set("X-Device-Id", deviceId.toString());
    return h;
  }

  private CreatePollRequest validRequest(String title) {
    LocalDate start = LocalDate.now().plusDays(10);
    return CreatePollRequest.builder()
        .title(title)
        .slots(
            List.of(
                CreatePollRequest.SlotRequest.builder()
                    .startDate(start)
                    .endDate(start.plusDays(3))
                    .build(),
                CreatePollRequest.SlotRequest.builder()
                    .startDate(start.plusDays(7))
                    .endDate(start.plusDays(10))
                    .build()))
        .build();
  }

  @Test
  void createPoll_member_returns201() {
    UUID tripId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();

    ResponseEntity<PollResponse> response =
        restTemplate.exchange(
            "/api/v1/trips/" + tripId + "/polls",
            HttpMethod.POST,
            new HttpEntity<>(validRequest("When should we go?"), headers(deviceId)),
            PollResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getId()).isNotNull();
    assertThat(response.getBody().getTitle()).isEqualTo("When should we go?");
    assertThat(response.getBody().getTripId()).isEqualTo(tripId);
    assertThat(response.getBody().getSlots()).hasSize(2);
  }

  @Test
  void listPolls_returnsPollsForTrip() {
    UUID tripId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();

    restTemplate.exchange(
        "/api/v1/trips/" + tripId + "/polls",
        HttpMethod.POST,
        new HttpEntity<>(validRequest("Poll 1"), headers(deviceId)),
        PollResponse.class);
    restTemplate.exchange(
        "/api/v1/trips/" + tripId + "/polls",
        HttpMethod.POST,
        new HttpEntity<>(validRequest("Poll 2"), headers(deviceId)),
        PollResponse.class);

    ResponseEntity<List<PollResponse>> response =
        restTemplate.exchange(
            "/api/v1/trips/" + tripId + "/polls",
            HttpMethod.GET,
            new HttpEntity<>(headers(deviceId)),
            new ParameterizedTypeReference<>() {});

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(2);
    assertThat(response.getBody())
        .extracting(PollResponse::getTitle)
        .containsExactlyInAnyOrder("Poll 1", "Poll 2");
  }

  @Test
  void createPoll_missingTitle_returns400() {
    UUID tripId = UUID.randomUUID();
    UUID deviceId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/trips/" + tripId + "/polls",
            HttpMethod.POST,
            new HttpEntity<>("{}", headers(deviceId)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void createPoll_missingDeviceId_returns401() {
    UUID tripId = UUID.randomUUID();

    HttpHeaders noDevice = new HttpHeaders();
    noDevice.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/trips/" + tripId + "/polls",
            HttpMethod.POST,
            new HttpEntity<>(validRequest("X"), noDevice),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
