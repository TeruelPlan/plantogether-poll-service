# Poll Service

> Date polling management service for trips

## Role in the Architecture

The Poll Service allows participants to collectively decide on trip dates. Each trip can have multiple
date polls with multiple proposed time slots. Participants vote (YES / MAYBE / NO), votes are weighted
to determine the winning slot, and the organizer locks the result — which automatically updates the trip
dates via RabbitMQ.

## Features

- Date poll creation with multiple time slots
- Weighted voting: YES (2 pts) / MAYBE (1 pt) / NO (0 pt)
- Slot locking by the organizer
- Trip membership verification via gRPC (TripService.IsMember)
- Publishing a `poll.locked` event consumed by Trip Service to update dates

## REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/trips/{tripId}/polls` | Create a poll |
| GET | `/api/v1/trips/{tripId}/polls` | List polls for the trip |
| GET | `/api/v1/polls/{pollId}` | Detail + response matrix |
| PUT | `/api/v1/polls/{pollId}/respond` | Vote (creates or updates the response) |
| PUT | `/api/v1/polls/{pollId}/lock` | Lock a slot (ORGANIZER) |

## gRPC Client

The Poll Service calls the Trip Service via gRPC before each operation:

- `TripService.IsMember(tripId, deviceId)` — verifies that the user is a trip member

## Data Model (`db_poll`)

**poll**

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID PK | Unique identifier (UUID v7) |
| `trip_id` | UUID NOT NULL | Trip reference |
| `title` | VARCHAR(255) NOT NULL | Poll title |
| `status` | ENUM NOT NULL | OPEN / LOCKED |
| `locked_slot_index` | INT NULLABLE | Index of the locked slot |
| `created_by` | UUID NOT NULL | device_id of the creator |
| `created_at` | TIMESTAMP NOT NULL | |
| `updated_at` | TIMESTAMP NOT NULL | |

**poll_slot**

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID PK | |
| `poll_id` | UUID NOT NULL FK→poll | |
| `start_date` | DATE NOT NULL | Slot start |
| `end_date` | DATE NOT NULL | Slot end |
| `slot_index` | INT NOT NULL | Display order |

**poll_response**

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID PK | |
| `poll_slot_id` | UUID NOT NULL FK→poll_slot | |
| `device_id` | UUID NOT NULL | Voter device UUID |
| `status` | ENUM NOT NULL | YES / MAYBE / NO |

Unique constraint: `(poll_slot_id, device_id)` — one vote per slot per user

## RabbitMQ Events (Exchange: `plantogether.events`)

**Publishes:**

| Routing Key | Trigger |
|-------------|---------|
| `poll.created` | Poll creation |
| `poll.locked` | Slot locking (Trip Service consumes this to update dates) |

**Consumes:** none

## Business Logic

The score for each slot is computed on the fly at query time:

```
Score = (nb_YES x 2) + (nb_MAYBE x 1)
```

The slot with the highest score is the suggested winner. The organizer is free to lock any slot.

## Configuration

```yaml
server:
  port: 8082

spring:
  application:
    name: plantogether-poll-service
  datasource:
    url: jdbc:postgresql://postgres:5432/db_poll
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate

grpc:
  client:
    trip-service:
      address: static://trip-service:9081
```

## Running Locally

```bash
# Prerequisites: docker compose up -d
# + plantogether-proto and plantogether-common installed

mvn spring-boot:run
```

## Dependencies

- **PostgreSQL 16** (`db_poll`): polls, slots, responses
- **RabbitMQ**: event publishing (`poll.created`, `poll.locked`)
- **Redis**: rate limiting (Bucket4j)
- **Trip Service** (gRPC 9081): trip membership verification
- **plantogether-proto**: gRPC contracts (client)
- **plantogether-common**: event DTOs, DeviceIdFilter, SecurityAutoConfiguration, CorsConfig

## Security

- Anonymous device-based identity: `X-Device-Id` header on every request
- `DeviceIdFilter` (from plantogether-common, auto-configured via `SecurityAutoConfiguration`) extracts the device UUID and sets the SecurityContext principal
- No JWT, no Keycloak, no login, no sessions
- Trip membership is verified via gRPC at each operation
- Only the ORGANIZER can lock a slot
- Zero PII stored (only `device_id` references)
