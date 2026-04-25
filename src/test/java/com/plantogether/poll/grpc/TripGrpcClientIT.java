package com.plantogether.poll.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.poll.AbstractIntegrationTest;
import com.plantogether.trip.grpc.GetTripMembersRequest;
import com.plantogether.trip.grpc.GetTripMembersResponse;
import com.plantogether.trip.grpc.IsMemberRequest;
import com.plantogether.trip.grpc.IsMemberResponse;
import com.plantogether.trip.grpc.TripMemberProto;
import com.plantogether.trip.grpc.TripServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TripGrpcClientIT extends AbstractIntegrationTest {

  private final FakeTripService fake = new FakeTripService();

  @Override
  protected TripServiceGrpc.TripServiceImplBase fakeTripService() {
    return fake;
  }

  @BeforeEach
  void resetFake() {
    fake.isMemberReturnsTrue = true;
    fake.isMemberError = null;
    fake.getMembersError = null;
  }

  static class FakeTripService extends TripServiceGrpc.TripServiceImplBase {
    volatile boolean isMemberReturnsTrue = true;
    volatile Status isMemberError = null;
    volatile Status getMembersError = null;

    @Override
    public void isMember(IsMemberRequest req, StreamObserver<IsMemberResponse> obs) {
      if (isMemberError != null) {
        obs.onError(isMemberError.asRuntimeException());
        return;
      }
      obs.onNext(
          IsMemberResponse.newBuilder()
              .setIsMember(isMemberReturnsTrue)
              .setRole(isMemberReturnsTrue ? "ORGANIZER" : "")
              .build());
      obs.onCompleted();
    }

    @Override
    public void getTripMembers(
        GetTripMembersRequest req, StreamObserver<GetTripMembersResponse> obs) {
      if (getMembersError != null) {
        obs.onError(getMembersError.asRuntimeException());
        return;
      }
      obs.onNext(
          GetTripMembersResponse.newBuilder()
              .addMembers(
                  TripMemberProto.newBuilder()
                      .setDeviceId(UUID.randomUUID().toString())
                      .setRole("PARTICIPANT")
                      .setDisplayName("Alice")
                      .build())
              .build());
      obs.onCompleted();
    }
  }

  @Test
  void isMember_success_returnsResponse() {
    IsMemberResponse response =
        tripGrpcClient.isMember(UUID.randomUUID().toString(), UUID.randomUUID().toString());

    assertThat(response.getIsMember()).isTrue();
    assertThat(response.getRole()).isEqualTo("ORGANIZER");
  }

  @Test
  void requireMember_nonMember_throwsAccessDenied() {
    fake.isMemberReturnsTrue = false;

    assertThatThrownBy(
            () ->
                tripGrpcClient.requireMember(
                    UUID.randomUUID().toString(), UUID.randomUUID().toString()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void isMember_unavailableServer_throwsServiceUnavailable() {
    fake.isMemberError = Status.UNAVAILABLE.withDescription("server down");

    assertThatThrownBy(
            () ->
                tripGrpcClient.isMember(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
  }

  @Test
  void isMember_internalError_throwsBadGateway() {
    fake.isMemberError = Status.INTERNAL.withDescription("boom");

    assertThatThrownBy(
            () ->
                tripGrpcClient.isMember(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
  }

  @Test
  void getTripMembers_success_returnsList() {
    List<TripMemberProto> members = tripGrpcClient.getTripMembers(UUID.randomUUID().toString());

    assertThat(members).hasSize(1);
    assertThat(members.get(0).getDisplayName()).isEqualTo("Alice");
    assertThat(members.get(0).getRole()).isEqualTo("PARTICIPANT");
  }

  @Test
  void getTripMembers_unavailable_throwsServiceUnavailable() {
    fake.getMembersError = Status.UNAVAILABLE;

    assertThatThrownBy(() -> tripGrpcClient.getTripMembers(UUID.randomUUID().toString()))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
  }
}
