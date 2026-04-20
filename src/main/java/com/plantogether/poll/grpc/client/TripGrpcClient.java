package com.plantogether.poll.grpc.client;

import com.plantogether.trip.grpc.GetTripMembersRequest;
import com.plantogether.trip.grpc.GetTripMembersResponse;
import com.plantogether.trip.grpc.IsMemberRequest;
import com.plantogether.trip.grpc.IsMemberResponse;
import com.plantogether.trip.grpc.TripMemberProto;
import com.plantogether.common.exception.AccessDeniedException;
import com.plantogether.trip.grpc.TripServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripGrpcClient {

    @Value("${grpc.trip-service.host:localhost}")
    private String host;

    @Value("${grpc.trip-service.port:9081}")
    private int port;

    private ManagedChannel channel;
    private TripServiceGrpc.TripServiceBlockingStub stub;

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        stub = TripServiceGrpc.newBlockingStub(channel);
        log.info("TripGrpcClient initialized → {}:{}", host, port);
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    public void requireMember(String tripId, String deviceId) {
        if (!isMember(tripId, deviceId).getIsMember()) {
            throw new AccessDeniedException("Device is not a member of this trip");
        }
    }

    public IsMemberResponse isMember(String tripId, String deviceId) {
        try {
            return stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .isMember(IsMemberRequest.newBuilder()
                            .setTripId(tripId)
                            .setDeviceId(deviceId)
                            .build());
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            log.error("IsMember gRPC call failed for trip={} device={}: {}", tripId, deviceId, e.getStatus());
            if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Trip membership verification temporarily unavailable");
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to verify trip membership");
        }
    }

    public List<TripMemberProto> getTripMembers(String tripId) {
        try {
            GetTripMembersResponse response = stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .getTripMembers(GetTripMembersRequest.newBuilder()
                            .setTripId(tripId)
                            .build());
            return response.getMembersList();
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            log.error("GetTripMembers gRPC call failed for trip={}: {}", tripId, e.getStatus());
            if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Trip members lookup temporarily unavailable");
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to fetch trip members");
        }
    }

    public void setStub(TripServiceGrpc.TripServiceBlockingStub stub) {
        this.stub = stub;
    }
}
