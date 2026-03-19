# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Run locally
mvn spring-boot:run
```

## Test Commands

```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=ClassName

# Run a specific test method
mvn test -Dtest=ClassName#methodName
```

Note: No tests exist yet — the test directory structure is in place but empty.

## Architecture Overview

This is a **Spring Boot 3.3.6 / Java 21 microservice** for managing date polls within a trip planning platform. It is
part of a larger microservices ecosystem.

### Key Integration Points

- **Keycloak** (OAuth2/JWT): All endpoints require authentication. `KeycloakJwtConverter` extracts roles from the
  `realm_access.roles` claim. JWK URI: `${KEYCLOAK_URL}/realms/plantogether/protocol/openid-connect/certs`.
- **PostgreSQL 16**: Schema managed by Flyway (`src/main/resources/db/migration/`). JPA DDL mode is `validate` — schema
  must exist before startup.
- **RabbitMQ**: AMQP messaging via `spring-boot-starter-amqp`.
- **Redis**: Caching via `spring-boot-starter-data-redis`.
- **Eureka**: Service discovery via `spring-cloud-starter-netflix-eureka-client`.
- **Spring Cloud Config**: Centralized config via `spring-cloud-starter-config`.
- **plantogether-common**: Internal shared library (1.0.0-SNAPSHOT) — must be available in local Maven repo.

### Package Structure

```
com.plantogether.poll/
├── config/         # Spring configuration beans
├── controller/     # REST controllers
├── dto/            # Request/response DTOs
├── model/          # JPA entities
├── repository/     # Spring Data JPA repositories
├── service/        # Business logic
├── security/       # SecurityConfig + KeycloakJwtConverter
└── exception/      # GlobalExceptionHandler (ResourceNotFoundException, AccessDeniedException, validation)
```

### Domain Model

- **Poll**: belongs to a trip, has status (open/locked/cancelled), `created_by` (Keycloak user ID), `locked_date`
- **PollSlot**: date/time slots within a poll
- **PollResponse**: a user's vote on a slot (`YES`=2pts, `MAYBE`=1pt, `NO`=0pt); highest score wins

### API Endpoints

```
POST   /api/trips/{tripId}/polls       - Create poll
GET    /api/trips/{tripId}/polls       - List polls for trip
GET    /api/polls/{pollId}             - Get poll details
PUT    /api/polls/{pollId}/respond     - Submit vote
PUT    /api/polls/{pollId}/lock        - Lock result (organizer only)
DELETE /api/polls/{pollId}             - Cancel poll (organizer only)
GET    /api/polls/{pollId}/results     - View computed results
```

### Security

- Stateless sessions, CSRF disabled
- All endpoints authenticated except `/actuator/health` and `/actuator/info`

### Required Environment Variables

| Variable             | Default                         |
|----------------------|---------------------------------|
| `DB_HOST`            | `localhost`                     |
| `DB_USER`            | —                               |
| `DB_PASSWORD`        | —                               |
| `RABBITMQ_HOST`      | `rabbitmq`                      |
| `RABBITMQ_PORT`      | `5672`                          |
| `RABBITMQ_USER`      | —                               |
| `RABBITMQ_PASSWORD`  | —                               |
| `REDIS_HOST`         | `redis`                         |
| `REDIS_PORT`         | `6379`                          |
| `KEYCLOAK_URL`       | `http://localhost:8180`         |
| `KEYCLOAK_REALM`     | `plantogether`                  |
| `KEYCLOAK_CLIENT_ID` | —                               |
| `EUREKA_URL`         | `http://localhost:8761/eureka/` |

### Ports

- Application listens on **8082**
- Health: `http://localhost:8082/actuator/health`
