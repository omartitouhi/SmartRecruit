# SmartRecruit — Backend (microservices)

Backend applicatif de SmartRecruit : un microservice Spring Boot par module fonctionnel,
routés via un API Gateway. Lancement de toute la stack, déploiement et CI : voir le
[README du monorepo](../README.md).

> **Avant de coder un endpoint ou un champ, lisez [DATA_MODEL.md](../docs/DATA_MODEL.md)** — c'est
> la référence commune au backend, au frontend et au service IA pour les structures de données,
> les enums et les contrats API partagés (notamment `MatchScore`, l'objet qui porte le score
> de compatibilité expliqué).

> Contrats d'API entre services et vers le frontend (entreprises, offres, candidatures, tableau de bord) :
> [API_CONTRACTS.md](../docs/API_CONTRACTS.md).

## Données de démonstration

Avec la stack démarrée (`docker compose up --build` à la racine) : `python backend/seed/seed_demo.py` crée
5 entreprises, 25 offres, 19 candidats (CV PDF analysés par le service IA), une cinquantaine de candidatures à tous
les statuts, une offre modérée et une offre clôturée. Rejouable sans doublon. Comptes (mot de passe `Motdepasse123`) :
`candidat@smartrecruit.test`, `recruteur@smartrecruit.test` (Acme Tech), `recruteur2` à `recruteur5@smartrecruit.test` ;
administrateur : voir `.env.example` à la racine. Repartir d'une base vide : `docker compose down -v`.

## Tests

- **Par service** : `mvn test` dans chaque module (H2 en mémoire, services voisins simulés).
- **De bout en bout** (environ 100 vérifications sur la stack démarrée : comptes, entreprises, offres, candidatures,
  matching, tableau de bord, administration, audit, contrôle d'accès) :

```bash
pip install -r backend/e2e/requirements.txt
python backend/e2e/e2e_full.py                  # docker compose
E2E_TARGET=k8s python backend/e2e/e2e_full.py   # cluster kind
```

## Architecture

```
                    ┌──────────────┐
                    │   Frontend    │  React SPA (:5173)        [frontend/]
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │ API Gateway   │  Spring Cloud Gateway (:8080)
                    └──────┬───────┘
        ┌──────────┬───────┼───────┬───────────┬───────────┐
        ▼          ▼       ▼       ▼           ▼           ▼
   auth-service user-service job-service application- dashboard-  ai-service
      :8081       :8082    :8083   service:8084  service:8085   :8000 (FastAPI)
        │          │        │         │             │        [ai-service/]
        └──────────┴────────┴─────────┴─────────────┴─────────────┘
                                   │
                          PostgreSQL + pgvector (:5432)
                  (une base logique par service : auth_db, user_db,
                   job_db, application_db, dashboard_db, ai_db)
```

## Microservices (dans ce dossier)

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
  d'environnement / noms Docker Compose (voir `docker-compose.yml` à la racine).
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
- Protection des entrées (`BE-SEC-03`), active dans chaque service :
  - **XSS** : toutes les chaînes des corps JSON sont converties en texte brut avant d'atteindre
    les contrôleurs (`security/SanitizingStringDeserializer`, jsoup) : balises, scripts et
    attributs `on*` supprimés, texte et retours à la ligne conservés. Les nouveaux endpoints sont
    protégés automatiquement. Seuls les champs annotés `@RawText` (mots de passe, tokens) sont
    exclus. Les réponses portent `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`,
    `X-Content-Type-Options: nosniff` et `X-Frame-Options: DENY`.
  - **CSRF** : non applicable, l'authentification passe par le header `Authorization` (jamais
    un cookie) et aucune session n'est créée ; la protection CSRF de Spring est donc désactivée.
  - **Injection SQL** : accès aux données exclusivement via Spring Data JPA / JPQL avec
    paramètres liés (requêtes préparées). Ne jamais construire de requête par concaténation.

## CI

Workflow `.github/workflows/backend.yml` à la racine du monorepo : build et tests Maven de chaque microservice
(matrice), déclenché par les changements sous `backend/`.
