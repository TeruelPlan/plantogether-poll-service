# Poll Service

> Service de gestion des sondages de dates pour les voyages

## Rôle dans l'architecture

Le Poll Service permet aux participants de décider collectivement des dates du voyage. Chaque voyage peut avoir
plusieurs sondages de dates avec 2 à 15 créneaux disponibles. Les participants votent (OUI/PEUT-ÊTRE/NON), et les votes
sont pondérés pour calculer automatiquement le créneau gagnant. L'organisateur valide le résultat qui verrouille les
dates du voyage.

## Fonctionnalités

- Création de sondages de dates (2-15 créneaux)
- Vote pondéré : OUI (2 pts) / PEUT-ÊTRE (1 pt) / NON (0 pt)
- Calcul automatique du créneau gagnant
- Verrouillage du résultat par l'organisateur
- Historique des votes
- Validation des dates
- Publication d'événements vers RabbitMQ

## Endpoints REST

| Méthode | Endpoint                      | Description                         |
|---------|-------------------------------|-------------------------------------|
| POST    | `/api/trips/{tripId}/polls`   | Créer un nouveau sondage            |
| GET     | `/api/trips/{tripId}/polls`   | Lister les sondages d'un voyage     |
| GET     | `/api/polls/{pollId}`         | Récupérer les détails d'un sondage  |
| PUT     | `/api/polls/{pollId}/respond` | Voter sur un créneau                |
| PUT     | `/api/polls/{pollId}/lock`    | Verrouiller le résultat (organizer) |
| DELETE  | `/api/polls/{pollId}`         | Annuler un sondage (organizer)      |
| GET     | `/api/polls/{pollId}/results` | Voir les résultats calculés         |

## Modèle de données

**Poll**

- `id` (UUID) : identifiant unique
- `trip_id` (UUID, FK) : voyage associé
- `title` (String) : titre du sondage
- `created_by` (UUID) : ID Keycloak du créateur
- `status` (ENUM: OPEN, LOCKED, CANCELLED) : état du sondage
- `created_at` (Timestamp)
- `locked_at` (Timestamp, nullable)
- `locked_date` (LocalDate, nullable) : date gagnante verrouillée

**PollSlot**

- `id` (UUID)
- `poll_id` (UUID, FK)
- `date` (LocalDate) : date proposée
- `start_time` (LocalTime, nullable)
- `end_time` (LocalTime, nullable)

**PollResponse**

- `id` (UUID)
- `poll_slot_id` (UUID, FK)
- `keycloak_id` (UUID) : votant
- `response` (ENUM: YES, MAYBE, NO)
- `responded_at` (Timestamp)

## Événements (RabbitMQ)

**Publie :**

- `PollCreated` — Émis lors de la création d'un nouveau sondage
- `PollLocked` — Émis quand l'organisateur verrouille une date
- `VoteSubmitted` — Émis lors d'un nouveau vote

**Consomme :**

- `TripCreated` — Pour initialiser les sondages si nécessaire
- `MemberJoined` — Pour préparer les réponses par défaut

## Configuration

```yaml
server:
  port: 8082
  servlet:
    context-path: /

spring:
  application:
    name: plantogether-poll-service
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  datasource:
    url: jdbc:postgresql://postgres:5432/plantogether_poll
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER}
    password: ${RABBITMQ_PASSWORD}
  redis:
    host: ${REDIS_HOST:redis}
    port: ${REDIS_PORT:6379}

keycloak:
  serverUrl: ${KEYCLOAK_SERVER_URL:http://keycloak:8080}
  realm: ${KEYCLOAK_REALM:plantogether}
  clientId: ${KEYCLOAK_CLIENT_ID}
```

## Lancer en local

```bash
# Prérequis : Docker Compose (infra), Java 21+, Maven 3.9+

# Option 1 : Maven
mvn spring-boot:run

# Option 2 : Docker
docker build -t plantogether-poll-service .
docker run -p 8082:8081 \
  -e KEYCLOAK_SERVER_URL=http://host.docker.internal:8080 \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  plantogether-poll-service
```

## Dépendances

- **Keycloak 24+** : authentification et autorisation
- **PostgreSQL 16** : persistance des sondages et votes
- **RabbitMQ** : publication d'événements
- **Redis** : cache des résultats calculés
- **Spring Boot 3.3.6** : framework web
- **Spring Cloud Netflix Eureka** : service discovery
- **Spring Security OAuth2** : validation des tokens porteur

## Logique métier

### Calcul du créneau gagnant

Le créneau avec le score pondéré le plus élevé gagne :

```
Score = (nombre_OUI × 2) + (nombre_PEUT_ÊTRE × 1)
```

### Validation

- Minimum 2 créneaux, maximum 15
- Les dates doivent être dans le futur
- Les créneaux ne peuvent être modifiés qu'avant verrouillage
- Un sondage peut avoir plusieurs résultats ex aequo

## Notes de sécurité

- Seul l'organisateur du voyage peut créer et verrouiller les sondages
- Tous les endpoints requièrent authentification Keycloak
- Les votes sont immuables après soumission
- Zéro PII stockée (seuls les UUIDs Keycloak)
