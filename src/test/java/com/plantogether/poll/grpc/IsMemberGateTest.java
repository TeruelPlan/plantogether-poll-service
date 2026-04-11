package com.plantogether.poll.grpc;

import com.plantogether.proto.trip.IsMemberRequest;
import com.plantogether.proto.trip.IsMemberResponse;
import com.plantogether.proto.trip.TripServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests verifying that poll-service enforces the IsMember gRPC gate
 * before accepting any write operation (R05 — CRITICAL gap).
 *
 * Acceptance criteria:
 * - POST /api/v1/trips/{tripId}/polls by a non-member → 403 Forbidden
 * - POST /api/v1/trips/{tripId}/polls by a member → processed (2xx or business error)
 * - IsMember gRPC stub is called exactly once per write request
 *
 * TDD RED PHASE: Tests are @Disabled because:
 * 1. poll-service has no REST controller yet (implementation pending)
 * 2. poll-service has no TripGrpcClient yet (implementation pending)
 * Remove @Disabled once poll-service write endpoints are implemented.
 *
 * Pattern: InProcessServerBuilder (grpc-testing) — no real network required.
 */
@Disabled("R05 RED PHASE — poll-service write endpoints not implemented yet")
class IsMemberGateTest {

    private static final String SERVER_NAME = "test-trip-grpc-" + UUID.randomUUID();

    private Server mockTripServer;
    private ManagedChannel channel;
    private CapturingTripServiceImpl mockTripService;

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
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        mockTripServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // R05-1: Non-member write → 403
    // -------------------------------------------------------------------------

    @Test
    void createPoll_byNonMember_returns403() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        // Stub: IsMember returns false
        mockTripService.stubIsMember(tripId, deviceId, false, "");

        // TODO: Replace with actual poll-service HTTP test client (MockMvc or RestAssured)
        // once TripGrpcClient and PollController are implemented.
        //
        // Expected call:
        //   POST /api/v1/trips/{tripId}/polls
        //   Header: X-Device-Id: {deviceId}
        //   Body: { "title": "When should we leave?", "slots": [...] }
        //
        // Expected response: 403 Forbidden
        //
        // Verification:
        //   assertThat(mockTripService.isMemberCallCount()).isEqualTo(1);
        //   assertThat(mockTripService.lastIsMemberRequest().getTripId()).isEqualTo(tripId.toString());

        // Placeholder assertion to satisfy JUnit — replace once controller exists
        org.junit.jupiter.api.Assertions.assertTrue(true,
                "Placeholder — implement once PollController + TripGrpcClient exist");
    }

    // -------------------------------------------------------------------------
    // R05-2: Member write → forwarded to business logic (not 403)
    // -------------------------------------------------------------------------

    @Test
    void createPoll_byMember_isForwardedToBusiness() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        // Stub: IsMember returns true with PARTICIPANT role
        mockTripService.stubIsMember(tripId, deviceId, true, "PARTICIPANT");

        // TODO: Replace with actual poll-service HTTP test client once implemented.
        //
        // Expected: response is NOT 403 (either 201 Created or a business-level error)
        //
        // Verification:
        //   assertThat(mockTripService.isMemberCallCount()).isEqualTo(1);
        //   assertThat(response.getStatus()).isNotEqualTo(403);

        org.junit.jupiter.api.Assertions.assertTrue(true,
                "Placeholder — implement once PollController + TripGrpcClient exist");
    }

    // -------------------------------------------------------------------------
    // R05-3: IsMember gRPC stub is called exactly once per write request
    // -------------------------------------------------------------------------

    @Test
    void createPoll_isMemberCalledExactlyOnce() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        mockTripService.stubIsMember(tripId, deviceId, false, "");

        // TODO: Trigger HTTP call and assert:
        //   assertThat(mockTripService.isMemberCallCount()).isEqualTo(1);

        org.junit.jupiter.api.Assertions.assertTrue(true,
                "Placeholder — implement once PollController + TripGrpcClient exist");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Minimal in-process TripService stub that captures IsMember calls
     * and returns configurable responses.
     */
    static class CapturingTripServiceImpl extends TripServiceGrpc.TripServiceImplBase {

        private boolean memberResult;
        private String roleResult;
        private int callCount;
        private IsMemberRequest lastRequest;

        void stubIsMember(UUID tripId, UUID deviceId, boolean isMember, String role) {
            this.memberResult = isMember;
            this.roleResult = role;
            this.callCount = 0;
        }

        int isMemberCallCount() {
            return callCount;
        }

        IsMemberRequest lastIsMemberRequest() {
            return lastRequest;
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
