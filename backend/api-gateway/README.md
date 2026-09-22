# SmartRecruit — API Gateway

Point d'entrée unique du système. Route les requêtes vers les microservices backend
(pas de service discovery type Eureka à cette échelle — résolution statique via
variables d'environnement / noms de services Docker Compose).

## Stack
- Java 21, Spring Boot 4.1, Spring Cloud Gateway (Server MVC)
- Port par défaut : `8080`

## Table de routage

| Route                        | Service cible          |
| ----------------------------- | ----------------------- |
| `/api/v1/auth/**`            | auth-service (8081)     |
| `/api/v1/users/**`           | user-service (8082)     |
| `/api/v1/jobs/**`            | job-service (8083)      |
| `/api/v1/applications/**`    | application-service (8084) |
| `/api/v1/dashboard/**`       | dashboard-service (8085) |
| `/api/v1/ai/**`               | ai-service (8000, FastAPI) |

## Lancer en local

```bash
./mvnw spring-boot:run
```

Voir `docker-compose.yml` à la racine de ce dépôt pour lancer toute la stack (gateway + microservices + DB).

## À faire (prochains sprints)
- Validation JWT au niveau du gateway (filtre `before`) pour éviter de dupliquer la logique d'auth dans chaque service.
- Rate limiting / CORS centralisé.
