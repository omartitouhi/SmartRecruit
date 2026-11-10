# SmartRecruit — Contrats d'API des modules restants

Complète `DATA_MODEL.md` (§1 conventions : UUID, dates ISO 8601 UTC, JSON camelCase, pagination
`{content,page,size,totalElements,totalPages}`, erreurs `{timestamp,status,error,message,path}` avec
`fieldErrors` `{champ: message}` sur les 400 de validation). **Ce fichier fait foi** pour les tickets
BE-COMP, BE-JOB, BE-APP, BE-CV-02, BE-MATCH, BE-DASH, BE-ADMIN ; le frontend s'adapte à ces chemins.

## 0. Règles communes

- Rôles : `CANDIDATE`, `RECRUITER`, `ADMIN` (identité signée par le gateway, déjà gérée par
  `GatewayIdentityFilter` / `AuthenticatedUser` de chaque service) et `SERVICE` (appels internes).
- Endpoints internes : `/internal/v1/**`, jamais routés par le gateway, `@PreAuthorize("hasRole('SERVICE')")`.
  Un service appelle un autre avec l'identité `SERVICE` signée (voir `InternalIdentityVerifier.addServiceIdentity`
  dans user-service et `ServiceRestClients` dans job-service ; secret `INTERNAL_AUTH_SECRET`).
- Clients HTTP Java : toujours `HttpClient.Version.HTTP_1_1` (uvicorn ne gère pas h2c), timeouts courts.
- URLs des services (propriétés `services.<nom>-uri`, variables `<NOM>_SERVICE_URI`) :
  `auth-service` 8081, `user-service` 8082, `job-service` 8083, `application-service` 8084,
  `dashboard-service` 8085, `ai-service` 8000 (préfixe `/api/v1/ai`).
- Les appels internes « best effort » (IA, audit, comptage) ne doivent jamais faire échouer la requête
  utilisateur : mode dégradé (valeur nulle / log warn sans donnée personnelle).
- Pas de migration : `ddl-auto: update`. Chaque module garde sa propre base.
- Validation et assainissement des champs texte comme dans les contrôleurs existants (`@RawText`, sanitizer).

## 1. user-service (BE-COMP-01..03, BE-CV-02)

`Company` = `{id, name, sector, description, logoUrl|null, members:[accountId]}` (réservé aux membres).
`CompanyPublic` = `{id, name, sector, description, logoUrl|null}`.

| Méthode et chemin | Rôle | Corps / réponse |
|---|---|---|
| `POST /api/v1/users/companies` | RECRUITER | `{name(req ≤150), sector(req ≤100), description(≤5000), logoUrl?(http/https ≤500)}` → 201 `Company`, créateur = premier membre ; **409** s'il a déjà une entreprise |
| `GET /api/v1/users/companies/me` | RECRUITER | `Company` ou 404 |
| `GET /api/v1/users/companies/{id}` | tout connecté | `CompanyPublic` ou 404 |
| `PATCH /api/v1/users/companies/{id}` | RECRUITER membre | JSON merge patch (name, sector, description, logoUrl) → `Company` ; 403 non-membre, 404 inconnue |
| `POST /api/v1/users/companies/{id}/invitations` | RECRUITER membre | `{email}` → 201 `{id,email,status:"PENDING",expiresAt,createdAt}` ; jeton aléatoire stocké **haché**, expire à 7 jours, non renvoyé dans l'API (notifié via un notifier journalisé, comme `PasswordResetNotifier`) ; doublon `PENDING` → 409 |
| `GET /api/v1/users/companies/{id}/invitations` | RECRUITER membre | `Invitation[]` (id,email,status `PENDING|ACCEPTED|EXPIRED`,expiresAt) |
| `POST /api/v1/users/companies/invitations/accept` | RECRUITER | `{token}` → `Company` ; rattache l'appelant ; 409 s'il a déjà une entreprise ; 400/410 jeton invalide ou expiré |
| `GET /api/v1/users/candidates/me/cv-suggestions` | CANDIDATE | données extraites du dernier CV analysé, récupérées chez l'IA (`GET /api/v1/ai/cv-parsing/candidates/{id}`), dates converties `YYYY-MM-DD`→`YYYY-MM` : `{skills[],experiences[{title,company,startDate,endDate|null,description}],education[{degree,institution,year}],languages[]}` ; 404 si aucun CV analysé, 503 si l'IA est indisponible. Le candidat valide puis enregistre via `PUT/PATCH /candidates/me` (BE-CV-02) |

`CandidateProfileResponse` gagne **`cvAnalysisStatus`** : `NONE` (pas de CV) | `PENDING` (upload fait, analyse en cours) |
`DONE` (`cvParsedAt` renseigné) | `FAILED` (l'IA a échoué : upload valide, mode dégradé). L'upload remet `PENDING`.

Internes :
- `GET /internal/v1/companies/by-member/{accountId}` → `{id,name,logoUrl}` (existant `CompanySummaryResponse`) ou 404.
- `GET /internal/v1/candidates/{id}/readiness` → `{profileComplete, hasCv, cvAnalysisStatus, cvParsedAt|null}` ;
  `profileComplete` = prénom, nom, email renseignés ; 404 si pas de profil.
- `GET /internal/v1/stats` → `{candidateProfiles, companies}`.
- Audit (best effort, asynchrone) : un RECRUITER/ADMIN qui consulte `GET /candidates/{id}` ou télécharge `GET /candidates/{id}/cv`
  déclenche `POST {dashboard}/internal/v1/audit-events` avec `action` `PROFILE_VIEWED` / `CV_VIEWED`.

## 2. job-service (BE-JOB-01..05, BE-ADMIN-02 côté offre)

`JobResponse` = `Job` de DATA_MODEL §3.3 + `updatedAt` + `moderationReason|null` (visible du propriétaire et de l'admin seulement).
`JobSummary` = `JobResponse` + `companyName|null` + `applicationsCount|null` (renseigné seulement avec `companyId=me`).

| Méthode et chemin | Rôle | Corps / réponse |
|---|---|---|
| `POST /api/v1/jobs` | RECRUITER | `{title(req ≤255), description(req 20..20000), requiredSkills(req, 1..50, ≤100 car.), experienceLevel, contractType, location(req ≤255)}` — pas de `companyId` (déduit de l'entreprise du recruteur via user-service ; **403** s'il n'en a pas) → 201 `JobResponse` `PUBLISHED`, `publishedAt` = maintenant ; 400 + `fieldErrors` ; après commit, analyse IA asynchrone best effort (`POST /api/v1/ai/job-analysis/analyze` `{jobId,title,description}`) |
| `GET /api/v1/jobs` | tout connecté | paramètres `keyword` (titre/description), `location`, `skills` (liste séparée par virgules, au moins une), `contractType`, `experienceLevel`, `companyId` (UUID ou `me`), `status` (admin / `me`), `page=0`, `size=20` (≤50). Candidats et défaut : **seulement `PUBLISHED`** ; `companyId=me` (RECRUITER) : toutes les offres de son entreprise + `applicationsCount` (appel interne, 0 si indisponible) ; ADMIN : tous statuts. Tri `publishedAt` décroissant → `Page<JobSummary>` |
| `GET /api/v1/jobs/{id}` | tout connecté | `JobSummary` ; un candidat ne voit que `PUBLISHED` (sinon 404) ; propriétaire/admin tout |
| `PATCH /api/v1/jobs/{id}` | RECRUITER propriétaire | merge patch (title, description, requiredSkills, experienceLevel, contractType, location) → `JobResponse`, `updatedAt` mis à jour, nouvelle analyse IA si titre/description/compétences changent ; **403** autre entreprise ; **409** si `CLOSED`/`MODERATED` |
| `PATCH /api/v1/jobs/{id}/status` | RECRUITER propriétaire | `{status}` transitions `DRAFT→PUBLISHED`, `PUBLISHED→CLOSED`, `CLOSED→PUBLISHED` ; autre transition → 400 ; offre `MODERATED` → 403 ; une offre clôturée disparaît de la recherche candidat |
| `DELETE /api/v1/jobs/{id}` | RECRUITER propriétaire | 204 ; seulement une offre `DRAFT` (jamais publiée, donc sans candidature ni favori) ; autre statut → **409** (clôturer l'offre) ; **403** autre entreprise, 404 inconnue |
| `GET /api/v1/jobs/recommendations` | CANDIDATE | existant (BE-REC-01) — le mapping doit rester prioritaire sur `/{id}` |

Internes :
- `GET /internal/v1/jobs/{id}` → `{id, companyId, title, status, updatedAt}` ou 404.
- `PATCH /internal/v1/jobs/{id}/moderation` `{reason}` → passe l'offre en `MODERATED`, enregistre `moderationReason` et `moderatedAt` → `JobResponse`.
- `GET /internal/v1/jobs/stats?companyId=` (optionnel) → `{total, draft, published, closed, moderated}`.

## 3. application-service (BE-APP-01..05, BE-MATCH-01..02)

`Application` = DATA_MODEL §3.4 (`id, jobId, candidateId, status, matchScore|null, appliedAt, statusHistory[{status,changedAt,changedBy}]`).
Stocker en plus `companyId` (copié de l'offre à la candidature). `matchScore` = JSON `MatchScore` (colonne JSON).

| Méthode et chemin | Rôle | Comportement |
|---|---|---|
| `POST /api/v1/applications` | CANDIDATE | `{jobId}` → 201 `Application` `RECEIVED` (historique initial) ; offre inconnue 404 ; offre non `PUBLISHED` 409 ; `readiness` `profileComplete && hasCv` sinon 422 ; doublon (même offre, candidature non `WITHDRAWN`) 409. Ensuite, **en arrière-plan**, score IA : `POST /api/v1/ai/matching/score` `{cvId: candidateId, jobId}` (timeout 15 s) puis enregistrement ; échec → `matchScore` reste null (la candidature reste valide) |
| `GET /api/v1/applications/me` | CANDIDATE | `Application[]`, plus récentes d'abord, y compris retirées |
| `GET /api/v1/applications?jobId=` | RECRUITER (entreprise propriétaire de l'offre) / ADMIN | `Application[]` triées par `matchScore.score` décroissant (null en dernier), sans les `WITHDRAWN` ; 403 autre entreprise |
| `PATCH /api/v1/applications/{id}/status` | RECRUITER propriétaire | `{status}` selon `ApplicationStatus.ALLOWED_TRANSITIONS` (jamais `WITHDRAWN`, réservé au candidat) → `Application` + ajout `statusHistory` ; transition interdite 400 ; événement d'audit `APPLICATION_STATUS_CHANGED` |
| `DELETE /api/v1/applications/{id}` | CANDIDATE propriétaire | 204, passe en `WITHDRAWN` (seulement depuis `RECEIVED`/`IN_REVIEW`, sinon 409) |
| `GET /api/v1/applications/match-preview?jobId=` | CANDIDATE | `MatchScore` du candidat connecté pour l'offre, avec **cache** (BE-MATCH-02) : clé `(candidateId, jobId)` valide tant que `cvParsedAt` du candidat et `updatedAt` de l'offre n'ont pas changé ; sinon appel IA (timeout 15 s) ; 404 offre inconnue ; 422 sans CV analysé ; 502/503 si l'IA échoue. La candidature réutilise ce cache |

Internes :
- `GET /internal/v1/applications/counts?jobIds=a,b` → `{ "<jobId>": n }` (hors `WITHDRAWN`).
- `GET /internal/v1/applications/stats?companyId=` (optionnel) → `{total, byStatus{...}, averageMatchScore|null, accepted, decided, acceptanceRate|null}` (`decided` = `ACCEPTED`+`REJECTED`).
- Audit best effort : changement de statut → `POST {dashboard}/internal/v1/audit-events`.
Appels sortants : job-service (`/internal/v1/jobs/{id}`), user-service (`/internal/v1/candidates/{id}/readiness`), ai-service.

## 4. auth-service (internes pour BE-ADMIN-01)

- `GET /internal/v1/accounts?role=&status=&page=0&size=20` → `Page<AccountResponse>`.
- `PATCH /internal/v1/accounts/{id}/status` `{status: ACTIVE|DISABLED}` → `AccountResponse` ; `DISABLED` révoque les refresh tokens ; **le login et le refresh refusent un compte non `ACTIVE`** (401/403 avec message clair) ; 404 inconnu.
- `GET /internal/v1/accounts/stats` → `{total, byRole{CANDIDATE,RECRUITER,ADMIN}, byStatus{ACTIVE,DISABLED,DELETED}}`.
- Création d'un administrateur de démarrage : si `ADMIN_EMAIL` et `ADMIN_PASSWORD` sont définis et qu'aucun compte n'a cet email,
  il est créé avec le rôle `ADMIN` au démarrage (propriété `auth.bootstrap-admin.*`). `/register` continue de refuser `ADMIN`.

## 5. dashboard-service (BE-DASH-01..02, BE-ADMIN-01..03)

| Méthode et chemin | Rôle | Réponse |
|---|---|---|
| `GET /api/v1/dashboard/stats/global?from=&to=` | ADMIN | `{activeJobs, totalApplications, totalUsers, usersByRole{CANDIDATE,RECRUITER,ADMIN}, averageMatchScore|null}` (agrège auth/job/application) |
| `GET /api/v1/dashboard/stats/company/{id}?from=&to=` | RECRUITER membre de `{id}` (vérifié via user-service `by-member`) / ADMIN | `{publishedJobs, applicationsReceived, acceptedApplications, acceptanceRate|null}` ; 403 sinon |
| `GET /api/v1/dashboard/admin/users?role=&status=&page=&size=` | ADMIN | `Page<AccountResponse>` (via auth-service) |
| `PATCH /api/v1/dashboard/admin/users/{id}/status` | ADMIN | `{status}` → `AccountResponse` ; audit `USER_STATUS_CHANGED` ; un admin ne peut pas se désactiver lui-même (400) |
| `PATCH /api/v1/dashboard/admin/jobs/{id}/moderation` | ADMIN | `{reason(req ≤500)}` → `JobResponse` (via job-service) ; audit `JOB_MODERATED` |
| `GET /api/v1/dashboard/admin/audit-log?from=&to=&actorId=&action=&page=&size=` | ADMIN | `Page<AuditLogEntryResponse>` (`id,actorId,action,resourceType,resourceId,timestamp`), plus récents d'abord ; `from`/`to` ISO 8601 |

Filtre de période des deux statistiques : `from` / `to` facultatifs (dates ISO `AAAA-MM-JJ`, incluses, UTC) ; seuls les chiffres des candidatures (total, acceptées, taux, entonnoir, délai de décision, score moyen) sont limités aux candidatures déposées dans la période ; offres, utilisateurs et entretiens à venir restent des valeurs instantanées. `from` > `to` ou date invalide → 400. Transmis à `GET /internal/v1/applications/stats?companyId=&from=&to=`.

Interne : `POST /internal/v1/audit-events` `{actorId, action, resourceType, resourceId}` → 201 (horodatage côté serveur).
Actions : `CV_VIEWED`, `PROFILE_VIEWED`, `APPLICATION_STATUS_CHANGED`, `USER_STATUS_CHANGED`, `JOB_MODERATED`.
Un module source d'une statistique indisponible ne casse pas la réponse : la valeur concernée est `null` (compteurs : 0 documenté).

## 6. Frontend

Chemins publics ci-dessus uniquement. `cvAnalysisStatus` remplace l'heuristique « cvUrl sans cvParsedAt ».
`matchPreview` appelle `GET /applications/match-preview?jobId=` (et non plus l'IA directement). La forme de `DashboardStats`
suit §5. Mode démo : un réglage par module (`VITE_MOCK_MODULES=jobs,applications,...`, `VITE_USE_MOCKS=true` = tous).

## 7. Enrichissements métier (hors Product Backlog)

Ajouts transverses qui ne correspondent à aucun ticket du backlog : ils sont référencés `EXT-*` dans le code,
les tests et les commits. Mêmes règles qu'au §0 (validation, assainissement, best effort, `ddl-auto: update`).
Les champs ajoutés aux entités existantes sont **nullables en base** : les lignes déjà présentes restent lisibles
et prennent la valeur par défaut indiquée à la lecture.

### 7.1 Offres enrichies — job-service (`EXT-JOB-01`)

Nouveaux champs de `Job` / `JobResponse` / `JobSummary` :

| Champ | Type | Règles |
|---|---|---|
| `workMode` | `WorkMode` (`ON_SITE` \| `HYBRID` \| `REMOTE`) | optionnel à la création (défaut `ON_SITE`), jamais `null` dans les réponses (une ligne ancienne vaut `ON_SITE`) ; ne peut pas être supprimé en PATCH |
| `salaryMin`, `salaryMax` | integer \| null | salaire brut **mensuel** en TND, `0..1 000 000` ; si les deux sont renseignés, `salaryMin ≤ salaryMax` (sinon 400 `fieldErrors.salaryMax`, y compris quand un PATCH n'envoie qu'une des deux bornes) ; supprimables en PATCH (`null`) |
| `applicationDeadline` | date `YYYY-MM-DD` \| null | dernier jour où l'on peut postuler (inclus, UTC) ; à la création et en PATCH elle doit être aujourd'hui ou plus tard (sinon 400 `fieldErrors.applicationDeadline`) ; supprimable en PATCH |
| `positions` | integer | nombre de postes, `1..100`, défaut `1` (une ligne ancienne vaut `1`) ; ne peut pas être supprimé en PATCH |

- **Clôture automatique** : une tâche planifiée (toutes les 5 min, `jobs.expiry.interval`, désactivable par
  `jobs.expiry.enabled`) passe en `CLOSED` toute offre `PUBLISHED` dont `applicationDeadline` est passée
  (`updatedAt` = maintenant).
- `PATCH /jobs/{id}/status` vers `PUBLISHED` (depuis `DRAFT` ou `CLOSED`) d'une offre dont la date limite est passée → **409**
  (« modifiez ou supprimez la date limite », ou dupliquez l'offre si elle est clôturée).
- **Recherche** `GET /api/v1/jobs` : nouveaux paramètres `workMode` et `minSalary` (entier ≥ 0 : garde les offres dont
  `salaryMax ≥ minSalary`, ou, sans `salaryMax`, dont `salaryMin ≥ minSalary` ; une offre sans salaire est exclue).
- **Dupliquer** `POST /api/v1/jobs/{id}/duplicate` (RECRUITER propriétaire) → 201 `JobResponse` : copie en `DRAFT`, `publishedAt`
  null, titre suffixé de « (copie) » (tronqué à 255), `applicationDeadline` vidée, autres champs copiés ; analyse IA
  demandée comme à la création. Offre `MODERATED` → 409 ; autre entreprise → 403 ; inconnue → 404.
- Interne `GET /internal/v1/jobs/{id}` gagne `applicationDeadline` ; `GET /internal/v1/jobs/stats` gagne `closingSoon`
  (offres `PUBLISHED` dont la date limite tombe dans les 7 prochains jours, aujourd'hui inclus).

### 7.2 Offres favorites — job-service (`EXT-SAVED-01`)

| Méthode et chemin | Rôle | Comportement |
|---|---|---|
| `PUT /api/v1/jobs/{id}/saved` | CANDIDATE | 204, idempotent ; offre inconnue ou non `PUBLISHED` → 404 ; au-delà de 200 favoris → 409 |
| `DELETE /api/v1/jobs/{id}/saved` | CANDIDATE | 204, idempotent (même si l'offre n'était pas en favori ou n'existe plus) |
| `GET /api/v1/jobs/saved` | CANDIDATE | `JobSummary[]` des favoris encore `PUBLISHED` ou `CLOSED` (les autres sont masqués), plus récemment ajoutés d'abord |

`JobSummary` gagne **`saved`** : `true`/`false` pour un CANDIDATE (recherche, détail, favoris), `null` pour les autres rôles.
Interne (RGPD) : `DELETE /internal/v1/candidates/{candidateId}/saved-jobs` → 204, idempotent.

### 7.3 Candidature motivée, décision expliquée, entretiens — application-service (`EXT-APP-01`, `EXT-APP-02`)

- `POST /api/v1/applications` accepte `{jobId, coverLetter?}` : lettre de motivation facultative, ≤ 5000 caractères (vide → null).
  Offre dont `applicationDeadline` est passée → **409** (même si la clôture automatique n'est pas encore passée).
- `PATCH /api/v1/applications/{id}/status` accepte `{status, message?, interview?}` :
  - `message` (≤ 2000, vide → null) : explication destinée au candidat, enregistrée dans l'entrée d'historique créée ;
  - `interview` n'est accepté qu'avec `status = INTERVIEW` (sinon 400) : `{scheduledAt (ISO 8601, dans le futur), durationMinutes (15..480, défaut 60), mode, location? (≤ 500)}`
    avec `InterviewMode = VIDEO | ON_SITE | PHONE` (`location` = adresse ou lien de visio).
- `PUT /api/v1/applications/{id}/interview` (RECRUITER de l'entreprise) : même corps `Interview`, planifie ou replanifie ;
  candidature hors `INTERVIEW` → 409. Audit `INTERVIEW_SCHEDULED` (aussi émis quand l'entretien est fourni au changement de statut).
- `GET /api/v1/applications/interviews/upcoming` (RECRUITER) : entretiens à venir de son entreprise (statut `INTERVIEW`,
  `scheduledAt ≥ maintenant`), plus proches d'abord, 50 au plus : `{applicationId, jobId, candidateId, scheduledAt, durationMinutes, mode, location}[]` ;
  `[]` sans entreprise.

`Application` gagne `coverLetter|null`, `interview|null` (`{scheduledAt, durationMinutes, mode, location|null}`) ;
chaque entrée de `statusHistory` gagne `message|null`. Le candidat voit sa lettre, ses messages et son entretien.

### 7.4 Notes et évaluation d'équipe — application-service (`EXT-APP-03`)

Réservé aux recruteurs de l'entreprise propriétaire (et à l'admin en lecture) ; **jamais visible du candidat**.

| Méthode et chemin | Rôle | Comportement |
|---|---|---|
| `GET /api/v1/applications/{id}/notes` | RECRUITER membre / ADMIN | `ApplicationNote[]` du plus ancien au plus récent |
| `POST /api/v1/applications/{id}/notes` | RECRUITER membre | `{content (1..2000), rating? (1..5)}` → 201 `ApplicationNote` |
| `PUT /api/v1/applications/{id}/notes/{noteId}` | RECRUITER auteur de la note | `{content (1..2000), rating? (1..5)}` → 200 `ApplicationNote` (contenu et évaluation remplacés) ; autre auteur → 403 ; inconnue → 404 ; 400 + `fieldErrors` |
| `DELETE /api/v1/applications/{id}/notes/{noteId}` | RECRUITER auteur de la note | 204 ; autre auteur → 403 ; inconnue → 404 |

`ApplicationNote` = `{id, applicationId, authorId, content, rating|null, createdAt}`.
Dans `GET /api/v1/applications?jobId=` (vue recruteur/admin), chaque `Application` gagne `notesCount` et `averageRating|null`
(moyenne des notes chiffrées, 1 décimale) ; ces deux champs valent `null` dans les réponses destinées au candidat.

Interne (RGPD) : `DELETE /internal/v1/candidates/{candidateId}/data` → 204, idempotent : vide `coverLetter` des candidatures du
candidat et supprime les notes qui les concernent. user-service l'appelle, comme `DELETE /internal/v1/candidates/{id}/saved-jobs`
de job-service, dans la propagation du droit à l'oubli (BE-SEC-02), avec les mêmes reprises.

### 7.5 Pilotage enrichi — application-service et dashboard-service (`EXT-DASH-01`)

- Interne `GET /internal/v1/applications/stats` gagne `averageDecisionDays|null` (moyenne, en jours à 1 décimale, entre `appliedAt`
  et la première entrée `ACCEPTED`/`REJECTED` de l'historique) et `upcomingInterviews` (nombre d'entretiens à venir).
- `GET /api/v1/dashboard/stats/company/{id}` gagne `applicationsByStatus` (`{RECEIVED, IN_REVIEW, INTERVIEW, ACCEPTED, REJECTED}`, l'entonnoir),
  `averageDecisionDays`, `upcomingInterviews` et `jobsClosingSoon`.
- `GET /api/v1/dashboard/stats/global` gagne `applicationsByStatus`, `averageDecisionDays` et `jobsClosingSoon`.
- Comme au §5, chaque valeur est `null` quand son service source est indisponible.

Nouvelle action d'audit : `INTERVIEW_SCHEDULED`.

### 7.6 Frontend (`EXT-FE-*`)

- Page publique d'une entreprise `/candidate/companies/:companyId` : `GET /users/companies/{id}` + `GET /jobs?companyId={id}` (aucun nouvel endpoint).
- Pipeline Kanban et export CSV des candidats d'une offre : construits côté client à partir de `GET /applications?jobId=`
  et des profils déjà chargés par la page de classement (aucun nouvel endpoint).
