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

## Endpoints

- `GET /api/v1/users/candidates/me` — (CANDIDATE) profil du candidat connecté, `404` s'il n'existe pas encore (BE-USER-03)
- `PATCH /api/v1/users/candidates/me` — (CANDIDATE) mise à jour partielle façon JSON Merge Patch : champ absent = inchangé, `null` = effacé (interdit pour firstName / lastName / email), valeur = remplacée ; `updatedAt` est mis à jour (BE-USER-03)
- `PUT /api/v1/users/candidates/me` — (CANDIDATE) crée (`201`) ou remplace entièrement (`200`) le profil du candidat connecté (BE-USER-01)
- `POST /api/v1/users/candidates/me/cv` — (CANDIDATE) upload du CV en `multipart/form-data`, champ `file`, PDF uniquement (type + signature `%PDF-` vérifiés), 5 Mo max ; remplace le CV précédent (BE-USER-02)
- `GET /api/v1/users/candidates/{id}/cv` — téléchargement du CV (cible de `cvUrl`) : le candidat lui-même, RECRUITER ou ADMIN
- `GET /api/v1/ping` — health check applicatif
- `GET /actuator/health` — health check Spring Boot Actuator

_À compléter au fil des sprints (voir Product Backlog)._
