# SmartRecruit — User Service

Profils Candidat et Recruteur, gestion des entreprises.

**Epics couverts (Product Backlog SmartRecruit) :** Candidate/Recruiter Management

## Stack
- Java 21, Spring Boot 4.1, Spring Data JPA, PostgreSQL
- Port par défaut : `8082`
- Base de données : `user_db`

## Lancer en local

```bash
./mvnw spring-boot:run
```

Nécessite une instance PostgreSQL locale (voir `docker-compose.yml` à la racine de ce dépôt pour lancer toute la stack).

## Structure

```
src/main/java/com/smartrecruit/user/
  controller/   # endpoints REST
  service/      # logique métier
  repository/   # accès données (Spring Data JPA)
  model/        # entités JPA
  dto/          # objets de transfert
  config/       # configuration (sécurité, CORS, etc.)
  exception/    # gestion des erreurs
```

## Endpoints

- `GET /api/v1/ping` — health check applicatif
- `GET /actuator/health` — health check Spring Boot Actuator

_À compléter au fil des sprints (voir Product Backlog)._
