# SmartRecruit — Backend (microservices)

Backend applicatif de SmartRecruit : un microservice Spring Boot par module fonctionnel,
routés via un API Gateway. Ce dépôt contient également l'orchestration Docker Compose de
toute la stack (backend + frontend + service IA + base de données).

## Prérequis

Ce dépôt suppose que `frontend/` et `ia/` sont clonés **à côté** de `backend/` (mêmes
parent) :

```
Recruitement/
  backend/     <- ce dépôt
  frontend/
  ia/
```

## Lancer toute la stack

```bash
cd backend
docker compose up --build
```

## Architecture

```
                    ┌──────────────┐
                    │   Frontend    │  React SPA (:5173)        [repo frontend/]
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │ API Gateway   │  Spring Cloud Gateway (:8080)
                    └──────┬───────┘
        ┌──────────┬───────┼───────┬───────────┬───────────┐
        ▼          ▼       ▼       ▼           ▼           ▼
   auth-service user-service job-service application- dashboard-  ai-service
      :8081       :8082    :8083   service:8084  service:8085   :8000 (FastAPI)
        │          │        │         │             │        [repo ia/]
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
- La validation JWT est dupliquée dans chaque service pour l'instant (TODO dans
  `auth-service/src/.../config/SecurityConfig.java`) — à centraliser au niveau du gateway
  si le temps le permet.

## CI

Un seul workflow (`.github/workflows/ci.yml`) construit et teste chaque microservice
(matrice Maven), déclenché sur push/PR vers `main`/`develop`.
