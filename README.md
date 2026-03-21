# Poll Service

> Service de gestion des sondages de dates pour les voyages

## Rôle dans l'architecture

Le Poll Service permet aux participants de décider collectivement des dates du voyage. Chaque voyage peut avoir
plusieurs sondages de dates avec plusieurs créneaux proposés. Les participants votent (YES / MAYBE / NO), les votes
sont pondérés pour déterminer le créneau gagnant, et l'organisateur verrouille le résultat — ce qui met à jour
automatiquement les dates du trip via RabbitMQ.

## Fonctionnalités

- Création de sondages de dates avec plusieurs créneaux
- Vote pondéré : YES (2 pts) / MAYBE (1 pt) / NO (0 pt)
- Verrouillage d'un créneau par l'organisateur
- Vérification d'appartenance au trip via gRPC (TripService.CheckMembership)
- Publication d'un événement `poll.locked` consommé par Trip Service pour mettre à jour les dates

## Endpoints REST

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/v1/trips/{tripId}/polls` | Créer un sondage |
| GET | `/api/v1/trips/{tripId}/polls` | Liste des sondages du voyage |
| GET | `/api/v1/polls/{pollId}` | Détail + matrice des réponses |
| PUT | `/api/v1/polls/{pollId}/respond` | Voter (crée ou met à jour la réponse) |
| PUT | `/api/v1/polls/{pollId}/lock` | Verrouiller un créneau (ORGANIZER) |

## gRPC Client

Le Poll Service appelle le Trip Service via gRPC avant chaque opération :

- `TripService.CheckMembership(tripId, userId)` — vérifie que l'utilisateur est membre du trip

## Modèle de données (`db_poll`)

**poll**

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | UUID PK | Identifiant unique (UUID v7) |
| `trip_id` | UUID NOT NULL | Référence au trip (keycloak_id externe) |
| `title` | VARCHAR(255) NOT NULL | Titre du sondage |
| `status` | ENUM NOT NULL | OPEN / LOCKED |
| `locked_slot_index` | INT NULLABLE | Index du créneau verrouillé |
| `created_by` | UUID NOT NULL | keycloak_id du créateur |
| `created_at` | TIMESTAMP NOT NULL | |
| `updated_at` | TIMESTAMP NOT NULL | |

**poll_slot**

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | UUID PK | |
| `poll_id` | UUID NOT NULL FK→poll | |
| `start_date` | DATE NOT NULL | Début du créneau |
| `end_date` | DATE NOT NULL | Fin du créneau |
| `slot_index` | INT NOT NULL | Ordre d'affichage |

**poll_response**

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | UUID PK | |
| `poll_slot_id` | UUID NOT NULL FK→poll_slot | |
| `keycloak_id` | UUID NOT NULL | Votant |
| `status` | ENUM NOT NULL | YES / MAYBE / NO |

Contrainte unique : `(poll_slot_id, keycloak_id)` — un vote par créneau par utilisateur

## Événements RabbitMQ (Exchange : `plantogether.events`)

**Publie :**

| Routing Key | Déclencheur |
|-------------|-------------|
| `poll.created` | Création d'un sondage |
| `poll.locked` | Verrouillage d'un créneau (Trip Service en est consommateur pour màj les dates) |

**Consomme :** aucun

## Logique métier

Le score de chaque créneau est calculé à la volée lors de la consultation :

```
Score = (nb_YES × 2) + (nb_MAYBE × 1)
```

Le créneau avec le score le plus élevé est le gagnant suggéré. L'organisateur reste libre de verrouiller
n'importe quel créneau.

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

## Lancer en local

```bash
# Prérequis : docker compose --profile essential up -d
# + plantogether-proto et plantogether-common installés

mvn spring-boot:run
```

## Dépendances

- **Keycloak 24+** : validation des tokens JWT
- **PostgreSQL 16** (`db_poll`) : sondages, créneaux, réponses
- **RabbitMQ** : publication d'événements (`poll.created`, `poll.locked`)
- **Redis** : rate limiting (Bucket4j)
- **Trip Service** (gRPC 9081) : vérification d'appartenance au trip
- **plantogether-proto** : contrats gRPC (client)
- **plantogether-common** : DTOs events, CorsConfig

## Sécurité

- Tous les endpoints requièrent un token Bearer Keycloak valide
- L'appartenance au trip est vérifiée via gRPC à chaque opération
- Seul l'ORGANIZER peut verrouiller un créneau
- Zero PII stockée (uniquement des `keycloak_id`)
