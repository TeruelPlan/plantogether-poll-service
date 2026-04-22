# CLAUDE.md

This file provides guidance to Claude when working with code in this repository.

## Commands

```bash
# Build
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Run locally
mvn spring-boot:run

# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=ClassName

# Run a specific test method
mvn test -Dtest=ClassName#methodName
```

**Prerequisites:**

Local Maven builds resolve shared libs (`plantogether-parent`, `plantogether-bom`, `plantogether-common`,
`plantogether-proto`) from GitHub Packages. Export a PAT with `read:packages` before running `mvn`:

```bash
export GITHUB_ACTOR=<your-github-username>
export PACKAGES_TOKEN=<your-PAT-with-read:packages>
mvn -s .settings.xml clean package
```

## Architecture

Spring Boot 3.5.9 microservice (Java 21). Manages date polling for trips: creating polls, collecting YES/MAYBE/NO
responses, and locking a winning slot.

**Ports:** REST `8082` · gRPC `9082` (server — not yet used, reserved for future consumers)

Real-time updates are no longer served here. Vote and lock events are published to RabbitMQ and
relayed to STOMP clients by notification-service on `/ws` (centralized STOMP hub).

**Package:** `com.plantogether.poll`

### Package structure

```
com.plantogether.poll/
├── config/          # RabbitConfig
├── controller/      # REST controllers
├── domain/          # JPA entities (Poll, PollSlot, PollResponse)
├── repository/      # Spring Data JPA
├── service/         # Business logic
├── dto/             # Request/Response DTOs (Lombok @Data @Builder)
├── grpc/
│   └── client/      # TripGrpcClient (IsMember call to trip-service:9081)
└── event/
    └── publisher/   # RabbitMQ publishers (PollCreated, PollLocked)
```

### Infrastructure dependencies

| Dependency | Default (local) | Purpose |
|---|---|---|
| PostgreSQL 16 | `localhost:5432/plantogether_poll` | Primary persistence (db_poll) |
| RabbitMQ | `localhost:5672` | Event publishing |
| Redis | `localhost:6379` | Rate limiting (Bucket4j) |
| trip-service gRPC | `localhost:9081` | IsMember before every write |


### Domain model (db_poll)

**`poll`** — id (UUID), trip_id (UUID), title, status (`OPEN`/`LOCKED`), locked_slot_index, created_by (device UUID), created_at, updated_at.

**`poll_slot`** — id (UUID), poll_id (FK), start_date, end_date, slot_index.

**`poll_response`** — id (UUID), poll_slot_id (FK), device_id, status (`YES`/`MAYBE`/`NO`).
Unique constraint: (poll_slot_id, device_id) — one response per user per slot.

Scoring: `YES` = 2 pts, `MAYBE` = 1 pt, `NO` = 0 pt. Highest total score wins when locking.

### gRPC client

Calls `TripGrpcService.IsMember(tripId, deviceId)` on trip-service:9081 before every write operation.
Returns 403 if `is_member = false`.

### REST API (`/api/v1/`)

| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/trips/{tripId}/polls` | X-Device-Id + member | Create poll |
| GET | `/api/v1/trips/{tripId}/polls` | X-Device-Id + member | List polls for trip |
| GET | `/api/v1/polls/{pollId}` | X-Device-Id + member | Poll detail + response matrix + scores |
| PUT | `/api/v1/polls/{pollId}/respond` | X-Device-Id + member | Submit or update vote (upsert) |
| PUT | `/api/v1/polls/{pollId}/lock` | X-Device-Id + ORGANIZER | Lock a slot → publish poll.locked event |

### Real-time broadcasting

This service no longer binds WebSockets. Vote and lock events are published to RabbitMQ
(`poll.vote.cast`, `poll.locked`) and relayed to STOMP clients by notification-service on
`/ws` → `/topic/trips/{tripId}/updates`. See notification-service `CLAUDE.md`.

### RabbitMQ events

**Publishes** (exchange `plantogether.events`):
- `poll.created` — routing key `poll.created` — on poll creation
- `poll.vote.cast` — routing key `poll.vote.cast` — on vote upsert (AFTER_COMMIT). Payload: `PollVoteCastEvent` from `plantogether-common`
- `poll.locked` — routing key `poll.locked` — on slot lock (consumed by notification-service and trip-service to update start/end dates)

### Security

- Anonymous device-based identity via `DeviceIdFilter` (from `plantogether-common`, auto-configured via `SecurityAutoConfiguration`)
- `X-Device-Id` header extracted and set as SecurityContext principal
- No JWT, no Keycloak, no login, no sessions
- No SecurityConfig.java needed — `SecurityAutoConfiguration` handles everything
- Principal name = device UUID string (`authentication.getName()`)
- Public endpoints: `/actuator/health`, `/actuator/info`
- ORGANIZER required for lock operation

### Environment variables

| Variable | Default |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_USER` | `plantogether` |
| `DB_PASSWORD` | `plantogether` |
| `RABBITMQ_HOST` | `localhost` |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_USER` | `guest` |
| `RABBITMQ_PASSWORD` | `guest` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `TRIP_SERVICE_GRPC_HOST` | `localhost` |
| `TRIP_SERVICE_GRPC_PORT` | `9081` |
