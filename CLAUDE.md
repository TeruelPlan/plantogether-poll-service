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

**Prerequisites:** install shared libs first:
```bash
cd ../plantogether-proto && mvn clean install
cd ../plantogether-common && mvn clean install
```

## Architecture

Spring Boot 3.3.6 microservice (Java 25). Manages date polling for trips: creating polls, collecting YES/MAYBE/NO
responses, and locking a winning slot.

**Ports:** REST `8082` · gRPC `9082` (server — not yet used, reserved for future consumers)

**Package:** `com.plantogether.poll`

### Package structure

```
com.plantogether.poll/
├── config/          # SecurityConfig, RabbitConfig
├── controller/      # REST controllers
├── domain/          # JPA entities (Poll, PollSlot, PollResponse)
├── repository/      # Spring Data JPA
├── service/         # Business logic
├── dto/             # Request/Response DTOs (Lombok @Data @Builder)
├── grpc/
│   └── client/      # TripGrpcClient (CheckMembership call to trip-service:9081)
└── event/
    └── publisher/   # RabbitMQ publishers (PollCreated, PollLocked)
```

### Infrastructure dependencies

| Dependency | Default (local) | Purpose |
|---|---|---|
| PostgreSQL 16 | `localhost:5432/plantogether_poll` | Primary persistence (db_poll) |
| RabbitMQ | `localhost:5672` | Event publishing |
| Redis | `localhost:6379` | Caching |
| Keycloak 24+ | `localhost:8180` realm `plantogether` | JWT validation via JWKS |
| trip-service gRPC | `localhost:9081` | CheckMembership before every write |


### Domain model (db_poll)

**`poll`** — id (UUID), trip_id (UUID), title, status (`OPEN`/`LOCKED`), locked_slot_index, created_by (Keycloak UUID), created_at, updated_at.

**`poll_slot`** — id (UUID), poll_id (FK), start_date, end_date, slot_index.

**`poll_response`** — id (UUID), poll_slot_id (FK), keycloak_id, status (`YES`/`MAYBE`/`NO`).
Unique constraint: (poll_slot_id, keycloak_id) — one response per user per slot.

Scoring: `YES` = 2 pts, `MAYBE` = 1 pt, `NO` = 0 pt. Highest total score wins when locking.

### gRPC client

Calls `TripGrpcService.CheckMembership(tripId, userId)` on trip-service:9081 before every write operation.
Returns 403 if `is_member = false`.

### REST API (`/api/v1/`)

| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/trips/{tripId}/polls` | Bearer JWT + member | Create poll |
| GET | `/api/v1/trips/{tripId}/polls` | Bearer JWT + member | List polls for trip |
| GET | `/api/v1/polls/{pollId}` | Bearer JWT + member | Poll detail + response matrix + scores |
| PUT | `/api/v1/polls/{pollId}/respond` | Bearer JWT + member | Submit or update vote (upsert) |
| PUT | `/api/v1/polls/{pollId}/lock` | Bearer JWT + ORGANIZER | Lock a slot → publish poll.locked event |

### RabbitMQ events

**Publishes** (exchange `plantogether.events`):
- `poll.created` — routing key `poll.created` — on poll creation
- `poll.locked` — routing key `poll.locked` — on slot lock (consumed by notification-service and trip-service to update start/end dates)

### Security

- Stateless JWT via `KeycloakJwtConverter` — `realm_access.roles` → `ROLE_<ROLE>` Spring authorities
- Principal name = Keycloak subject UUID
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
| `KEYCLOAK_URL` | `http://localhost:8180` |
| `TRIP_SERVICE_GRPC_HOST` | `localhost` |
| `TRIP_SERVICE_GRPC_PORT` | `9081` |

