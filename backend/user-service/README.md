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

Les CV sont stockés dans `CV_STORAGE_DIR` (défaut `./data/cvs`, volume `cv_data` en Docker),
**chiffrés au repos en AES-256-GCM** (BE-SEC-01) avec la clé `CV_ENCRYPTION_KEY` (32 octets en
base64, à générer avec `openssl rand -base64 32` ; la valeur par défaut ne sert qu'au dev local).
Le service refuse de démarrer sans clé valide. Les CV ne sont déchiffrés que lors d'un
téléchargement autorisé ; un fichier altéré est détecté (tag GCM) et n'est pas servi.

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

## Effacement RGPD (BE-SEC-02)

Après une suppression de compte (`BE-USER-04`), `gdpr/PersonalDataErasureProcessor` propage
l'effacement aux services qui détiennent encore des données du candidat :

| Service | Appel | Effet |
| --- | --- | --- |
| auth-service | `DELETE /internal/v1/accounts/{id}` | compte anonymisé (`DELETED`), sessions révoquées |
| ai-service | `DELETE /api/v1/ai/candidates/{id}/data` | résultats d'analyse IA supprimés (contrat : `DATA_MODEL.md` §4.6) |

Premier essai juste après la suppression, puis nouvel essai toutes les
`GDPR_ERASURE_RETRY_INTERVAL` (5 min) jusqu'à confirmation des deux services. Chaque étape
réussie n'est pas rejouée. Le suivi (`deleted_candidates`) conserve la date limite légale
(suppression + 30 jours) ; une suppression encore incomplète après cette date est journalisée
en `ERROR`. Variables : `AUTH_SERVICE_URI`, `AI_SERVICE_URI`.

## Audit

Lorsqu'un RECRUITER ou un ADMIN lit `GET /candidates/{id}` ou télécharge un CV, un événement
`PROFILE_VIEWED` / `CV_VIEWED` est envoyé en tâche de fond à `POST {DASHBOARD_SERVICE_URI}/internal/v1/audit-events`
(par défaut `http://localhost:8085`). Un échec est journalisé sans donnée personnelle et n'affecte jamais la réponse.

## Endpoints

- `GET /api/v1/users/candidates/me` — (CANDIDATE) profil du candidat connecté, `404` s'il n'existe pas encore (BE-USER-03)
- `PATCH /api/v1/users/candidates/me` — (CANDIDATE) mise à jour partielle façon JSON Merge Patch : champ absent = inchangé, `null` = effacé (interdit pour firstName / lastName / email), valeur = remplacée ; `updatedAt` est mis à jour (BE-USER-03)
- `PUT /api/v1/users/candidates/me` — (CANDIDATE) crée (`201`) ou remplace entièrement (`200`) le profil du candidat connecté (BE-USER-01)
- `POST /api/v1/users/candidates/me/cv` — (CANDIDATE) upload du CV en `multipart/form-data`, champ `file`, PDF uniquement (type + signature `%PDF-` vérifiés), 5 Mo max ; remplace le CV précédent (BE-USER-02)
- `GET /api/v1/users/candidates/{id}/cv` — téléchargement du CV (cible de `cvUrl`) : le candidat lui-même, RECRUITER ou ADMIN
- `DELETE /api/v1/users/candidates/me?confirm=true` — (CANDIDATE) droit à l'oubli : profil et CV supprimés immédiatement, seul un marqueur sans donnée personnelle est conservé ; toute réécriture ultérieure renvoie `410` (BE-USER-04)
- `GET /api/v1/users/candidates/{id}` — (RECRUITER, ADMIN) profil d'un candidat ; pour un compte supprimé : `{"id": ..., "deleted": true}` sans aucune donnée personnelle
- `GET /api/v1/users/candidates/me/cv-suggestions` — (CANDIDATE) données extraites du dernier CV analysé (ai-service), dates en `YYYY-MM`, à valider puis enregistrer via PUT/PATCH ; `404` sans CV analysé, `503` si l'IA est indisponible (BE-CV-02). `cvAnalysisStatus` (`NONE|PENDING|DONE|FAILED`) du profil indique l'état de l'analyse
- `POST /api/v1/users/companies` — (RECRUITER) crée l'entreprise, le créateur en est le premier membre ; `409` s'il en a déjà une (BE-COMP-01)
- `GET /api/v1/users/companies/me` — (RECRUITER) son entreprise avec la liste des membres, `404` sinon (BE-COMP-01)
- `GET /api/v1/users/companies/{id}` — (tout connecté) vue publique sans les membres (BE-COMP-01)
- `PATCH /api/v1/users/companies/{id}` — (RECRUITER membre) JSON Merge Patch ; `403` pour un non-membre (BE-COMP-02)
- `POST` / `GET /api/v1/users/companies/{id}/invitations` — (RECRUITER membre) invite un recruteur par email (jeton aléatoire stocké haché, valable 7 jours, lien journalisé faute de serveur mail) / liste les invitations ; `409` si une invitation est déjà en attente (BE-COMP-03)
- `POST /api/v1/users/companies/invitations/accept` — (RECRUITER) `{token}` rattache l'appelant à l'entreprise ; `400` jeton inconnu, `410` expiré ou déjà utilisé, `409` s'il a déjà une entreprise (BE-COMP-03)
- `GET /internal/v1/companies?ids=...` — (SERVICE, non routé par le gateway) nom et logo d'entreprises, utilisé par job-service pour les recommandations (BE-REC-01)
- `GET /internal/v1/companies/by-member/{accountId}` — (SERVICE) entreprise d'un recruteur `{id,name,logoUrl}`, `404` sinon
- `GET /internal/v1/candidates/{id}/readiness` — (SERVICE) `{profileComplete, hasCv, cvAnalysisStatus, cvParsedAt}`, utilisé par application-service
- `GET /internal/v1/stats` — (SERVICE) `{candidateProfiles, companies}` pour dashboard-service
- `GET /api/v1/ping` — health check applicatif
- `GET /actuator/health` — health check Spring Boot Actuator

_À compléter au fil des sprints (voir Product Backlog)._
