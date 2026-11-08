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

Voir `docker-compose.yml` à la racine du monorepo pour lancer toute la stack (gateway + microservices + DB).

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

## Rate limiting (BE-SEC-05)

`security/RateLimitingFilter` limite les endpoints sensibles (`security.rate-limit.paths` :
login, register, refresh, forgot-password, reset-password) à `max-requests` appels par IP et
par endpoint sur une fenêtre glissante `window` (défaut : 10 en 5 min). Au-delà : `429` avec
le header `Retry-After` (secondes), sans appel au microservice.

| Variable | Défaut | Rôle |
| --- | --- | --- |
| `RATE_LIMIT_MAX_REQUESTS` | `10` | appels autorisés par fenêtre |
| `RATE_LIMIT_WINDOW` | `5m` | durée de la fenêtre glissante |
| `RATE_LIMIT_TRUST_FORWARDED_FOR` | `false` | utiliser `X-Forwarded-For` comme IP client (seulement derrière un reverse proxy de confiance) |

Compteurs en mémoire : suffisant pour une instance unique du gateway, un stockage partagé
(Redis) serait nécessaire avec plusieurs instances. Ce filtre complète le verrouillage par
compte de l'auth-service (5 échecs en 15 min, `BE-AUTH-02`).

## À faire (prochains sprints)
- CORS centralisé.
