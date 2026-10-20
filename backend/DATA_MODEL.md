# SmartRecruit — Modèle de données partagé

Ce document est la **source de vérité** pour les structures de données échangées entre
`smartrecruit-backend` (6 microservices), `smartrecruit-frontend` et `smartrecruit-ia`.

**Règle d'équipe : toute modification d'un champ, d'un enum ou d'un contrat listé ici doit
être répercutée dans ce fichier dans la même PR.** Si un champ que vous consommez n'y est
pas, ajoutez-le avant de coder dessus plutôt que d'improviser un nom différent.

Chaque microservice reste propriétaire de sa base de données (schéma interne libre), mais
la **forme JSON exposée par ses endpoints REST doit respecter ce document**.

---

## 1. Conventions générales

| Sujet | Règle |
|---|---|
| Format des identifiants | UUID v4 (string), champ `id`. Exception : les IDs auto-incrémentés internes à une base ne sont jamais exposés tels quels dans l'API. |
| Dates / horodatages | ISO 8601 UTC, ex. `2026-09-26T14:32:00Z`. Champs `createdAt`, `updatedAt` (pas de `created_at`). |
| Casse JSON | `camelCase` partout (requêtes et réponses), y compris pour les réponses du service IA (Python/FastAPI doit sérialiser en camelCase, pas en snake_case). |
| Pagination | `{ "content": [...], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 }` |
| Erreurs | `{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "...", "path": "/api/v1/..." }` (format Spring Boot par défaut, à répliquer côté `ai-service`) |
| Auth | Header `Authorization: Bearer <jwt>` sur tous les endpoints protégés. Le JWT contient `sub` (userId), `role`, `exp`. |
| Base path | `/api/v1/<domaine>` partout, y compris `ai-service` (`/api/v1/ai/<module>`) — cf. table de routage dans `smartrecruit-backend/README.md`. |
| Fichiers | Upload en `multipart/form-data`, champ `file`. Taille max CV : 5 Mo, type : `application/pdf` uniquement. |

---

## 2. Enums partagés

Ces valeurs doivent être **identiques à la lettre** (majuscules, underscore) dans les 3 repos : les enums Java, les types TypeScript et les `Literal`/`Enum` Python.

```
Role            = CANDIDATE | RECRUITER | ADMIN
AccountStatus   = ACTIVE | DISABLED | DELETED
JobStatus       = DRAFT | PUBLISHED | CLOSED | MODERATED
ContractType    = CDI | CDD | STAGE | ALTERNANCE | FREELANCE
ExperienceLevel = JUNIOR | INTERMEDIATE | SENIOR | LEAD
ApplicationStatus = RECEIVED | IN_REVIEW | INTERVIEW | ACCEPTED | REJECTED | WITHDRAWN
```

Transitions autorisées pour `ApplicationStatus` (cf. `BE-APP-04`) :
`RECEIVED → IN_REVIEW → INTERVIEW → {ACCEPTED, REJECTED}`, et `RECEIVED/IN_REVIEW → WITHDRAWN` (par le candidat uniquement). Toute autre transition est rejetée (400).

---

## 3. Entités par service

### 3.1 `auth-service` (Wael) — base `auth_db`

**Account**
| Champ | Type | Notes |
|---|---|---|
| id | UUID | |
| email | string | unique |
| passwordHash | string | jamais exposé dans l'API |
| role | Role | |
| status | AccountStatus | |
| createdAt / updatedAt | datetime | |

### 3.2 `user-service` (Wael pour Candidate, Kadhem pour Company) — base `user_db`

**CandidateProfile**
| Champ | Type | Notes |
|---|---|---|
| id | UUID | = accountId (1:1 avec Account) |
| firstName, lastName | string | obligatoires |
| email | string | obligatoire — email de contact du candidat (visible par les recruteurs), pas forcément celui du compte |
| phone | string \| null | |
| location | string \| null | ville, pour le filtre de recherche d'offres |
| skills | string[] | déclaratif, complété par l'extraction IA (`AI-CV-02`) |
| experiences | Experience[] | `{ title, company, startDate, endDate\|null, description }` |
| education | Education[] | `{ degree, institution, year }` |
| languages | string[] | |
| cvUrl | string \| null | référence au fichier stocké (chiffré, cf. `BE-SEC-01`), jamais l'URL brute du stockage : endpoint de téléchargement `/api/v1/users/candidates/{id}/cv` |
| cvParsedAt | datetime \| null | horodatage de la dernière analyse IA réussie |
| cvAnalysisStatus | enum | `NONE` (pas de CV) \| `PENDING` (analyse en cours) \| `DONE` \| `FAILED` (IA indisponible : l'upload reste valide, mode dégradé) — `BE-CV-02` |
| createdAt / updatedAt | datetime | |
| deleted | boolean | réponse API uniquement : `true` si le candidat a supprimé son compte (`BE-USER-04`) ; tous les champs personnels sont alors `null` (afficher « Compte supprimé ») |

**Company**
| Champ | Type | Notes |
|---|---|---|
| id | UUID | |
| name, sector, description | string | |
| logoUrl | string \| null | |
| members | UUID[] | accountId des recruteurs rattachés (un recruteur n'appartient qu'à une entreprise) |

Contrats complets des entreprises, invitations, offres, candidatures, statistiques et administration : [API_CONTRACTS.md](API_CONTRACTS.md).

### 3.3 `job-service` (Kadhem) — base `job_db`

**Job**
| Champ | Type | Notes |
|---|---|---|
| id | UUID | |
| companyId | UUID | FK logique vers `Company` |
| title, description | string | |
| requiredSkills | string[] | alimenté manuellement, croisé avec `AI-JOB-01` |
| experienceLevel | ExperienceLevel | |
| contractType | ContractType | |
| location | string | |
| status | JobStatus | |
| publishedAt | datetime \| null | |
| updatedAt | datetime | dernière modification ; sert aussi d'invalidation au cache des scores |
| moderationReason | string \| null | raison du retrait par un admin (`BE-ADMIN-02`), visible du propriétaire et des admins seulement |

### 3.4 `application-service` (Kadhem) — base `application_db`

**Application**
| Champ | Type | Notes |
|---|---|---|
| id | UUID | |
| jobId | UUID | |
| candidateId | UUID | |
| companyId | UUID | entreprise propriétaire de l'offre, copiée à la candidature (stockage interne, non exposé) |
| status | ApplicationStatus | |
| matchScore | MatchScore \| null | voir §4 — objet complet, pas juste le nombre ; `null` tant que l'IA n'a pas répondu ou si elle a échoué |
| appliedAt | datetime | |
| statusHistory | `{ status, changedAt, changedBy }[]` | pour l'audit (`BE-ADMIN-03`) |

### 3.5 `dashboard-service` (Omar pour Dashboard, Kadhem pour Administration) — base `dashboard_db`

**AuditLogEntry**
| Champ | Type | Notes |
|---|---|---|
| id | UUID | |
| actorId | UUID | qui a fait l'action |
| action | string | ex. `CV_VIEWED`, `USER_DISABLED` |
| resourceType / resourceId | string / UUID | |
| timestamp | datetime | |

### 3.6 `ai-service` (Omar) — base `ai_db`, LLM via OpenRouter

Tables `parsed_cvs` (dernier CV analysé par candidat) et `job_analyses` (offre analysée), voir §4 pour les contrats d'API.
Aucun embedding ni `pgvector` : le matching est évalué par un LLM, le score global est calculé par le code.

---

## 4. Contrats inter-services critiques

Ce sont les formes JSON les plus sensibles du projet : c'est ce qui porte
**l'explicabilité**, le vrai différenciateur de SmartRecruit (cf. slide 7 du dossier).
Tout changement ici impacte au minimum `ai-service` + `application-service` + `frontend`.

### 4.1 `MatchScore` — réponse de `POST /api/v1/ai/matching/score`

```json
{
  "score": 78.5,
  "criteria": [
    {
      "label": "Compétences techniques",
      "weight": 0.5,
      "contribution": 42.0,
      "explanation": "8 des 10 compétences requises sont présentes dans le profil (Java, Spring Boot, ...)."
    },
    {
      "label": "Expérience",
      "weight": 0.3,
      "contribution": 24.0,
      "explanation": "4 ans d'expérience pour un niveau requis SENIOR (3-5 ans)."
    }
  ]
}
```
Miroir exact du schéma Pydantic `app/schemas/common.py` dans `smartrecruit-ia`
(`MatchScore`, `MatchCriterion`). Si vous ajoutez un champ ici, ajoutez-le aussi côté
Pydantic et dans le DTO Java de `application-service`.

### 4.2 `CVParsingResult` — réponse de `POST /api/v1/ai/cv-parsing/parse`

```json
{
  "skills": ["Java", "Spring Boot", "React"],
  "experiences": [
    { "title": "Backend Developer", "company": "Acme", "startDate": "2022-01", "endDate": null, "description": "..." }
  ],
  "education": [
    { "degree": "Master Génie Logiciel", "institution": "ESPRIT", "year": 2025 }
  ],
  "languages": ["Français", "Anglais"]
}
```

### 4.3 `JobAnalysisResult` — réponse de `POST /api/v1/ai/job-analysis/analyze`

```json
{
  "requiredSkills": ["Java", "Spring Boot", "PostgreSQL"],
  "experienceLevel": "SENIOR",
  "seniority": "SENIOR"
}
```

### 4.4 `SkillGapResult` — réponse de `GET /api/v1/ai/skill-gap/candidates/{id}/jobs/{jobId}`

```json
{
  "missingSkills": ["Kubernetes", "GraphQL"]
}
```

### 4.5 `TokenResponse` — réponse de `POST /api/v1/auth/login` et `POST /api/v1/auth/refresh`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "q3V0b2tlbi1vcGFxdWUtYmFzZTY0dXJs",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```
- `accessToken` : JWT HS256 (`sub` = accountId, `role`, `iat`, `exp`), valide 1 h.
- `refreshToken` : jeton opaque (pas un JWT), valide 7 jours, stocké haché (SHA-256) côté `auth-service`.
  Usage unique : `/refresh` le révoque et renvoie une nouvelle paire (rotation). Expiré / révoqué → `401`.
- `expiresIn` : durée de vie de l'`accessToken` en secondes.

Identifiants invalides → `401` avec un message générique (pas de distinction email / mot de passe).
5 échecs en 15 min sur le même compte → compte bloqué 15 min, `429`.

### 4.6 Effacement RGPD — `DELETE /api/v1/ai/candidates/{candidateId}/data` (`BE-SEC-02`)

Appelé par `user-service` après la suppression d'un compte candidat (`BE-USER-04`). Le
service IA doit supprimer **toutes** les données liées au candidat (résultats de parsing du CV,
embeddings `pgvector`, scores et recommandations en cache).

- Réponse attendue : `204` (ou tout `2xx`), y compris si aucune donnée n'existe (idempotent).
- Toute autre réponse est considérée comme un échec : `user-service` réessaie toutes les 5 min.
  Au-delà de 30 jours après la demande, l'échec est journalisé en erreur (`RGPD : effacement ... incomplet`).
- Appel de service à service (pas via le gateway), headers signés `X-Auth-User-Role: SERVICE`
  (cf. `README.md`, section sécurité).

L'équivalent côté `auth-service` est `DELETE /internal/v1/accounts/{accountId}` : le compte passe
en `DELETED`, email et mot de passe sont remplacés, toutes les sessions sont révoquées.

### 4.7 Recommandations (`AI-REC-01` → `BE-REC-01`)

**Réponse attendue de l'IA** — `GET /api/v1/ai/recommendation/candidates/{candidateId}/jobs`
(appelé par `job-service`, pas via le gateway) :

```json
{
  "recommendations": [
    { "jobId": "7b0c...-uuid", "score": 91.0 },
    { "jobId": "1f3a...-uuid", "score": 72.5 }
  ]
}
```
`score` sur 100, même échelle que `MatchScore.score` (§4.1). Toute réponse non `2xx` est traitée
comme une indisponibilité (le frontend reçoit `503`).

**Réponse exposée au frontend** — `GET /api/v1/jobs/recommendations?limit=10` (rôle CANDIDATE) :

```json
[
  {
    "jobId": "7b0c...-uuid",
    "title": "Data Engineer",
    "companyId": "c41d...-uuid",
    "companyName": "Globex",
    "location": "Tunis",
    "contractType": "CDI",
    "experienceLevel": "SENIOR",
    "publishedAt": "2026-10-01T09:00:00Z",
    "score": 91.0
  }
]
```
Triée par score décroissant, uniquement les offres `PUBLISHED`, `limit` entre 1 et 50 (défaut 10).
`companyName` vaut `null` si `user-service` est momentanément indisponible.

---

## 5. Où modifier quoi

| Vous changez... | Répercutez dans... |
|---|---|
| Un champ d'entité (§3) | Le modèle JPA du service concerné + le type TypeScript frontend correspondant (`smartrecruit-frontend/src/types/`) + ce fichier |
| Un contrat IA (§4) | Le schéma Pydantic (`smartrecruit-ia/app/schemas/`) + le DTO Java qui le consomme (`application-service` ou `job-service`) + le type TypeScript frontend + ce fichier |
| Un enum (§2) | Les 3 repos (Java enum, TS union type, Python `Enum`/`Literal`) + ce fichier |

En cas de doute sur un nom de champ, ce fichier fait autorité — pas le code existant
(qui peut être en retard sur une décision d'équipe).

---

## 6. Où trouver les structures déjà codées

Les entités, enums et contrats ci-dessus existent déjà en code, prêts à être utilisés
(complétez-les, ne les redéfinissez pas ailleurs) :

| Repo | Emplacement |
|---|---|
| `smartrecruit-backend` | Un package `model/` (entités JPA + enums) et `dto/` (réponses API) par microservice, ex. `auth-service/src/main/java/com/smartrecruit/auth/model/Account.java` |
| `smartrecruit-frontend` | `src/types/enums.ts`, `src/types/entities.ts`, `src/types/contracts.ts`, `src/types/api.ts` — tout réexporté depuis `src/types/index.ts` |
| `smartrecruit-ia` | `app/schemas/enums.py` (enums partagés) et `app/schemas/common.py` (`MatchScore`, `CVParsingResult`, `JobAnalysisResult`, `SkillGapResult`) — sérialisés en camelCase via `CamelModel` |

Les routers `cv_parsing`, `job_analysis`, `matching`, `skill_gap` du service IA déclarent
déjà leur `response_model` avec ces schémas.
