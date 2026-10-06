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

## Authentification (BE-SEC-04)

`security/JwtAuthenticationGatewayFilter` valide le JWT une seule fois pour toute la plateforme :
- routes publiques (`security.public-paths` : register, login, refresh, logout,
  forgot-password, reset-password) et requêtes `OPTIONS` : laissées passer ;
- toute autre requête `/api/**` sans JWT valide : `401` renvoyé par le gateway, le
  microservice n'est pas appelé ;
- JWT valide : l'identité est transmise dans des headers signés (HMAC-SHA256 avec
  `INTERNAL_AUTH_SECRET`) : `X-Auth-User-Id`, `X-Auth-User-Role`, `X-Auth-Timestamp`,
  `X-Auth-Signature`. Les headers `X-Auth-*` fournis par le client sont toujours écrasés.

Variables d'environnement : `JWT_SECRET` (identique à auth-service) et `INTERNAL_AUTH_SECRET`
(identique à tous les microservices).

## À faire (prochains sprints)
- Rate limiting / CORS centralisé.
