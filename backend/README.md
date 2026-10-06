# SmartRecruit — Backend (microservices)

Backend applicatif de SmartRecruit : un microservice Spring Boot par module fonctionnel,
routés via un API Gateway. Ce dépôt contient également l'orchestration Docker Compose de
toute la stack (backend + frontend + service IA + base de données).

> **Avant de coder un endpoint ou un champ, lisez [DATA_MODEL.md](DATA_MODEL.md)** — c'est
> la référence commune aux 3 dépôts (backend, frontend, ia) pour les structures de données,
> les enums et les contrats API partagés (notamment `MatchScore`, l'objet qui porte le score
> de compatibilité expliqué).

## Prérequis

Ce dépôt suppose que `smartrecruit-frontend/` et `smartrecruit-ia/` sont clonés **à côté**
de `smartrecruit-backend/` (même parent) :

```
Recruitement/
  smartrecruit-backend/     <- ce dépôt
  smartrecruit-frontend/
  smartrecruit-ia/
```

## Lancer toute la stack

```bash
cd smartrecruit-backend
docker compose up --build
```

## Architecture

```
                    ┌──────────────┐
                    │   Frontend    │  React SPA (:5173)        [repo smartrecruit-frontend/]
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │ API Gateway   │  Spring Cloud Gateway (:8080)
                    └──────┬───────┘
        ┌──────────┬───────┼───────┬───────────┬───────────┐
        ▼          ▼       ▼       ▼           ▼           ▼
   auth-service user-service job-service application- dashboard-  ai-service
      :8081       :8082    :8083   service:8084  service:8085   :8000 (FastAPI)
        │          │        │         │             │        [repo smartrecruit-ia/]
        └──────────┴────────┴─────────┴─────────────┴─────────────┘
                                   │
                          PostgreSQL + pgvector (:5432)
                  (une base logique par service : auth_db, user_db,
                   job_db, application_db, dashboard_db, ai_db)
```

## Microservices (dans ce dépôt)

| Service | Port | Rôle |
| --- | --- | --- |
| `api-gateway` | 8080 | Routage, point d'entrée unique |
| `auth-service` | 8081 | Authentification, JWT, RBAC |
| `user-service` | 8082 | Profils candidat/recruteur, entreprises |
| `job-service` | 8083 | Offres d'emploi |
| `application-service` | 8084 | Candidatures, suivi |
| `dashboard-service` | 8085 | Statistiques, administration, audit |

Chaque microservice a son propre `README.md`, sa structure en couches
(`controller/ service/ repository/ model/ dto/ config/ exception/`) et son `Dockerfile`.

## Stack
- Java 21, Spring Boot 4.1
- Spring Data JPA, PostgreSQL
- Spring Security + JWT (`auth-service`)
- Spring Cloud Gateway — Server MVC (`api-gateway`)
- Docker Compose

## Décision d'architecture

Le dossier de validation académique (slide 16) recommandait un **monolithe modulaire**
+ un service IA dédié, jugé plus adapté à une équipe de 4 étudiants sur 12 semaines.
L'équipe a choisi de partir sur de **vraies microservices** pour approfondir
l'architecture distribuée. Points d'attention :

- Pas de service discovery (Eureka) pour l'instant — résolution statique via variables
  d'environnement / noms Docker Compose (voir `docker-compose.yml`).
- Chaque service a sa propre base logique (isolation des données), mais partage la même
  instance PostgreSQL en local pour simplifier le développement.
- La validation JWT est **centralisée au niveau du gateway** (`BE-SEC-04`) : une requête
  `/api/**` sans JWT valide est rejetée en `401` par le gateway (sauf les routes publiques
  d'authentification). Pour un JWT valide, le gateway transmet l'identité aux microservices
  dans des headers internes signés (HMAC-SHA256, secret `INTERNAL_AUTH_SECRET`) :
  `X-Auth-User-Id`, `X-Auth-User-Role`, `X-Auth-Timestamp`, `X-Auth-Signature`. Les headers
  `X-Auth-*` envoyés par un client sont écrasés.
- Chaque microservice vérifie seulement cette signature (`security/InternalIdentityVerifier`)
  et applique le RBAC (`BE-AUTH-04`) avec `@PreAuthorize("hasRole('RECRUITER')")`.
  L'utilisateur courant s'obtient avec `@AuthenticationPrincipal AuthenticatedUser user`.
  Un appel direct à un service (sans passer par le gateway) est rejeté en `401`.

## CI

Un seul workflow (`.github/workflows/ci.yml`) construit et teste chaque microservice
(matrice Maven), déclenché sur push/PR vers `main`/`develop`.
