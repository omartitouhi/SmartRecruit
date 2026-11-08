# SmartRecruit — Job Service

Publication et gestion des offres d'emploi.

**Epics couverts (Product Backlog SmartRecruit) :** Job Management

## Stack
- Java 21, Spring Boot 4.1, Spring Data JPA, PostgreSQL
- Port par défaut : `8083`
- Base de données : `job_db`

## Lancer en local

```bash
./mvnw spring-boot:run
```

Nécessite une instance PostgreSQL locale (voir `docker-compose.yml` à la racine du monorepo pour lancer toute la stack).

## Structure

```
src/main/java/com/smartrecruit/job/
  controller/   # endpoints REST
  service/      # logique métier
  repository/   # accès données (Spring Data JPA)
  model/        # entités JPA
  dto/          # objets de transfert
  config/       # configuration (sécurité, CORS, etc.)
  exception/    # gestion des erreurs
```

## Services appelés

| Service | Appel | Variable |
| --- | --- | --- |
| ai-service | `GET /api/v1/ai/recommendation/candidates/{id}/jobs` | `AI_SERVICE_URI` |
| user-service | `GET /internal/v1/companies?ids=...` (noms des entreprises) | `USER_SERVICE_URI` |

Appels directs (hors gateway) avec l'identité signée `SERVICE`.

## Endpoints

- `GET /api/v1/jobs/recommendations?limit=10` — (CANDIDATE) offres recommandées par l'IA, enrichies (titre, entreprise, lieu, contrat, niveau) et triées par score (BE-REC-01, contrat : `DATA_MODEL.md` §4.7). `503` si l'ai-service est indisponible.
- `GET /api/v1/ping` — health check applicatif
- `GET /actuator/health` — health check Spring Boot Actuator

_À compléter au fil des sprints (voir Product Backlog)._
