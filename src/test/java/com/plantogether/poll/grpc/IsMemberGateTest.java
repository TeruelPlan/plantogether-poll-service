package com.plantogether.poll.grpc;

import com.plantogether.poll.controller.PollController;
import com.plantogether.poll.domain.Poll;
import com.plantogether.poll.event.publisher.PollEventPublisher.PollCreatedInternalEvent;
import com.plantogether.poll.exception.GlobalExceptionHandler;
import com.plantogether.poll.grpc.client.TripGrpcClient;
import com.plantogether.poll.repository.PollRepository;
import com.plantogether.poll.service.PollService;
import com.plantogether.trip.grpc.IsMemberRequest;
import com.plantogether.trip.grpc.IsMemberResponse;
import com.plantogether.trip.grpc.TripServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
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

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying poll-service enforces the IsMember gRPC gate
 * before accepting any write operation (R05).
 *
 * Pattern: InProcessServerBuilder (grpc-testing) + standalone MockMvc.
 * A real TripGrpcClient is wired against an in-process gRPC server stubbing TripService.
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
                """.formatted(a, a.plusDays(6), b, b.plusDays(6));
    }

    @BeforeEach
    void setUp() throws IOException {
        mockTripService = new CapturingTripServiceImpl();
        mockTripServer = InProcessServerBuilder
                .forName(SERVER_NAME)
                .directExecutor()
                .addService(mockTripService)
                .build()
                .start();

        channel = InProcessChannelBuilder
                .forName(SERVER_NAME)
                .directExecutor()
                .build();

        TripGrpcClient tripGrpcClient = new TripGrpcClient();
        tripGrpcClient.setStub(TripServiceGrpc.newBlockingStub(channel));

        PollRepository pollRepository = mock(PollRepository.class);
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        when(pollRepository.save(any(Poll.class))).thenAnswer(inv -> {
            Poll p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            p.getSlots().forEach(s -> s.setId(UUID.randomUUID()));
            return p;
        });

        PollService pollService = new PollService(pollRepository, tripGrpcClient, applicationEventPublisher);
        PollController controller = new PollController(pollService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
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

        mockMvc.perform(post("/api/v1/trips/{tripId}/polls", tripId)
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

        mockMvc.perform(post("/api/v1/trips/{tripId}/polls", tripId)
                        .principal(authentication)
                        .header("X-Device-Id", DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated());

        assertThat(mockTripService.callCount).isEqualTo(1);
        verify(applicationEventPublisher).publishEvent(any(PollCreatedInternalEvent.class));
    }

    @Test
    void createPoll_isMemberCalledExactlyOnce() throws Exception {
        UUID tripId = UUID.randomUUID();
        mockTripService.stubIsMember(false, "");

        mockMvc.perform(post("/api/v1/trips/{tripId}/polls", tripId)
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
        public void isMember(IsMemberRequest request, StreamObserver<IsMemberResponse> responseObserver) {
            callCount++;
            lastRequest = request;
            responseObserver.onNext(IsMemberResponse.newBuilder()
                    .setIsMember(memberResult)
                    .setRole(roleResult)
                    .build());
            responseObserver.onCompleted();
        }
    }
}
