package com.plantogether.poll;

import com.plantogether.poll.grpc.client.TripGrpcClient;
import com.plantogether.trip.grpc.TripServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Testcontainers
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("plantogether_poll")
          .withUsername("plantogether")
          .withPassword("plantogether");

  @Container
  static final RabbitMQContainer RABBIT =
      new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-alpine"));

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.rabbitmq.host", RABBIT::getHost);
    registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired protected TestRestTemplate restTemplate;
  @Autowired protected TripGrpcClient tripGrpcClient;

  private Server grpcServer;
  private ManagedChannel grpcChannel;

  @BeforeEach
  void startInProcessGrpc() throws IOException {
    String serverName = InProcessServerBuilder.generateName();
    grpcServer =
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(fakeTripService())
            .build()
            .start();
    grpcChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    tripGrpcClient.setStub(TripServiceGrpc.newBlockingStub(grpcChannel));
  }

  @AfterEach
  void stopInProcessGrpc() throws InterruptedException {
    if (grpcChannel != null) {
      grpcChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (grpcServer != null) {
      grpcServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  protected abstract TripServiceGrpc.TripServiceImplBase fakeTripService();
}
