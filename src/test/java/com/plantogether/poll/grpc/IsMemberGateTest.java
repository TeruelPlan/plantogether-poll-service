package com.plantogether.poll.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantogether.poll.controller.PollController;
import com.plantogether.poll.controller.PollDetailController;
import com.plantogether.poll.domain.Poll;
import com.plantogether.poll.domain.PollResponse;
import com.plantogether.poll.domain.PollSlot;
import com.plantogether.poll.domain.PollStatus;
import com.plantogether.poll.event.publisher.PollEventPublisher.PollCreatedInternalEvent;
import com.plantogether.poll.exception.GlobalExceptionHandler;
import com.plantogether.poll.grpc.client.TripGrpcClient;
import com.plantogether.poll.repository.PollRepository;
import com.plantogether.poll.repository.PollResponseRepository;
import com.plantogether.poll.service.PollResponseInsertHelper;
import com.plantogether.poll.service.PollResponseService;
import com.plantogether.poll.service.PollService;
import com.plantogether.trip.grpc.IsMemberRequest;
import com.plantogether.trip.grpc.IsMemberResponse;
import com.plantogether.trip.grpc.TripServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Integration tests verifying poll-service enforces the IsMember gRPC gate before accepting any
 * write operation (R05).
 *
 * <p>Pattern: InProcessServerBuilder (grpc-testing) + standalone MockMvc. A real TripGrpcClient is
 * wired against an in-process gRPC server stubbing TripService.
 */
class IsMemberGateTest {

  private static final String SERVER_NAME = "test-trip-grpc-" + UUID.randomUUID();
  private static final String DEVICE_ID = UUID.randomUUID().toString();

  private Server mockTripServer;
  private ManagedChannel channel;
  private CapturingTripServiceImpl mockTripService;
  private MockMvc mockMvc;
  private Authentication authentication;
  private ApplicationEventPublisher applicationEventPublisher;
  private PollResponseRepository pollResponseRepository;
  private PollResponseInsertHelper insertHelperRef;
  private Poll existingPoll;
  private PollSlot existingSlot;

  private static String validBody() {
    LocalDate a = LocalDate.now().plusMonths(2);
    LocalDate b = a.plusDays(14);
    return """
    {
      "title": "When to leave?",
      "slots": [
        {"startDate": "%s", "endDate": "%s"},
        {"startDate": "%s", "endDate": "%s"}
      ]
    }
    """
        .formatted(a, a.plusDays(6), b, b.plusDays(6));
  }

  @BeforeEach
  void setUp() throws IOException {
    mockTripService = new CapturingTripServiceImpl();
    mockTripServer =
        InProcessServerBuilder.forName(SERVER_NAME)
            .directExecutor()
            .addService(mockTripService)
            .build()
            .start();

    channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();

    TripGrpcClient tripGrpcClient = new TripGrpcClient();
    tripGrpcClient.setStub(TripServiceGrpc.newBlockingStub(channel));

    PollRepository pollRepository = mock(PollRepository.class);
    applicationEventPublisher = mock(ApplicationEventPublisher.class);
    when(pollRepository.save(any(Poll.class)))
        .thenAnswer(
            inv -> {
              Poll p = inv.getArgument(0);
              p.setId(UUID.randomUUID());
              p.getSlots().forEach(s -> s.setId(UUID.randomUUID()));
              return p;
            });

    pollResponseRepository = mock(PollResponseRepository.class);
    PollService pollService =
        new PollService(
            pollRepository, pollResponseRepository, tripGrpcClient, applicationEventPublisher);
    PollController controller = new PollController(pollService);

    existingPoll =
        Poll.builder()
            .id(UUID.randomUUID())
            .tripId(UUID.randomUUID())
            .title("When?")
            .status(PollStatus.OPEN)
            .createdBy(UUID.randomUUID())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    existingSlot =
        PollSlot.builder()
            .id(UUID.randomUUID())
            .poll(existingPoll)
            .startDate(LocalDate.now().plusMonths(1))
            .endDate(LocalDate.now().plusMonths(1).plusDays(2))
            .slotIndex(0)
            .build();
    existingPoll.getSlots().add(existingSlot);
    when(pollRepository.findById(existingPoll.getId())).thenReturn(Optional.of(existingPoll));
    when(pollResponseRepository.findByPollSlot_IdAndDeviceId(any(), any()))
        .thenReturn(Optional.empty());
    when(pollResponseRepository.save(any(PollResponse.class)))
        .thenAnswer(
            inv -> {
              PollResponse pr = inv.getArgument(0);
              pr.setId(UUID.randomUUID());
              return pr;
            });

    PollResponseInsertHelper insertHelper = mock(PollResponseInsertHelper.class);
    insertHelperRef = insertHelper;
    when(insertHelper.insertNew(any(), any(), any()))
        .thenAnswer(
            inv -> {
              PollResponse pr =
                  PollResponse.builder()
                      .id(UUID.randomUUID())
                      .pollSlot(inv.getArgument(0))
                      .deviceId(inv.getArgument(1))
                      .status(inv.getArgument(2))
                      .build();
              return pr;
            });
    when(pollResponseRepository.findByPollSlot_Id(any())).thenReturn(List.of());
    PollResponseService pollResponseService =
        new PollResponseService(
            pollRepository,
            pollResponseRepository,
            insertHelper,
            tripGrpcClient,
            applicationEventPublisher);
    PollDetailController detailController =
        new PollDetailController(pollResponseService, pollService);

    mockMvc =
        MockMvcBuilders.standaloneSetup(controller, detailController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    authentication =
        new UsernamePasswordAuthenticationToken(
            DEVICE_ID, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    SecurityContextHolder.clearContext();
    channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    mockTripServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }

  @Test
  void createPoll_byNonMember_returns403() throws Exception {
    UUID tripId = UUID.randomUUID();
    mockTripService.stubIsMember(false, "");

    mockMvc
        .perform(
            post("/api/v1/trips/{tripId}/polls", tripId)
                .principal(authentication)
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
        .andExpect(status().isForbidden());

    assertThat(mockTripService.callCount).isEqualTo(1);
    assertThat(mockTripService.lastRequest.getTripId()).isEqualTo(tripId.toString());
    verify(applicationEventPublisher, org.mockito.Mockito.never())
        .publishEvent(any(PollCreatedInternalEvent.class));
  }

  @Test
  void createPoll_byMember_isForwardedToBusinessAndPublishesEvent() throws Exception {
    UUID tripId = UUID.randomUUID();
    mockTripService.stubIsMember(true, "PARTICIPANT");

    mockMvc
        .perform(
            post("/api/v1/trips/{tripId}/polls", tripId)
                .principal(authentication)
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
        .andExpect(status().isCreated());

    assertThat(mockTripService.callCount).isEqualTo(1);
    verify(applicationEventPublisher).publishEvent(any(PollCreatedInternalEvent.class));
  }

  @Test
  void respond_byNonMember_returns403() throws Exception {
    mockTripService.stubIsMember(false, "");
    String body =
        """
        {"slotId": "%s", "status": "YES"}
        """
            .formatted(existingSlot.getId());

    mockMvc
        .perform(
            put("/api/v1/polls/{pollId}/respond", existingPoll.getId())
                .principal(authentication)
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    assertThat(mockTripService.callCount).isEqualTo(1);
    verify(pollResponseRepository, org.mockito.Mockito.never()).save(any(PollResponse.class));
    verify(insertHelperRef, org.mockito.Mockito.never()).insertNew(any(), any(), any());
  }

  @Test
  void respond_byMember_isForwardedToBusiness() throws Exception {
    mockTripService.stubIsMember(true, "PARTICIPANT");
    String body =
        """
        {"slotId": "%s", "status": "YES"}
        """
            .formatted(existingSlot.getId());

    mockMvc
        .perform(
            put("/api/v1/polls/{pollId}/respond", existingPoll.getId())
                .principal(authentication)
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    assertThat(mockTripService.callCount).isEqualTo(1);
    verify(insertHelperRef).insertNew(any(), any(), any());
  }

  @Test
  void createPoll_isMemberCalledExactlyOnce() throws Exception {
    UUID tripId = UUID.randomUUID();
    mockTripService.stubIsMember(false, "");

    mockMvc.perform(
        post("/api/v1/trips/{tripId}/polls", tripId)
            .principal(authentication)
            .header("X-Device-Id", DEVICE_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(validBody()));

    assertThat(mockTripService.callCount).isEqualTo(1);
  }

  static class CapturingTripServiceImpl extends TripServiceGrpc.TripServiceImplBase {

    boolean memberResult;
    String roleResult = "";
    int callCount;
    IsMemberRequest lastRequest;

    void stubIsMember(boolean isMember, String role) {
      this.memberResult = isMember;
      this.roleResult = role;
      this.callCount = 0;
      this.lastRequest = null;
    }

    @Override
    public void isMember(
        IsMemberRequest request, StreamObserver<IsMemberResponse> responseObserver) {
      callCount++;
      lastRequest = request;
      responseObserver.onNext(
          IsMemberResponse.newBuilder().setIsMember(memberResult).setRole(roleResult).build());
      responseObserver.onCompleted();
    }
  }
}
