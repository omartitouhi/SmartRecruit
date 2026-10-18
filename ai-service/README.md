# SmartRecruit — AI Service

Service IA dédié : CV Parsing, Job Analysis, Matching (évaluation sémantique par LLM),
Recommandation, Skill Gap. Chaque score de compatibilité est toujours accompagné d'une
explication critère par critère (voir slide 7 du dossier — Explicabilité).

**Modules couverts :** IA-01 (CV Parsing) · IA-02 (Job Analysis) · IA-03 (Matching) ·
IA-04 (Recommandation) · IA-05 (Skill Gap)

**Important — Responsible AI (slide 18) :** le service IA n'automatise jamais la décision
finale de recrutement (human-in-the-loop). Les résultats de matching doivent être validés
par un schéma JSON strict et rester explicables.

> **Structures de données partagées :** les schémas Pydantic (`app/schemas/`) doivent rester
> synchronisés avec `DATA_MODEL.md` dans `smartrecruit-backend` — c'est la référence commune
> aux 3 dépôts pour `MatchScore`, `CVParsingResult`, `JobAnalysisResult`, etc.

## Stack
- Python 3.12, FastAPI, Uvicorn
- LLM via [OpenRouter](https://openrouter.ai) (API compatible OpenAI, modèle configurable)
- PostgreSQL `ai_db` (CV parsés, offres analysées — tables créées au démarrage)
- Port par défaut : `8000`

## Lancer en local

```bash
python -m venv .venv
source .venv/bin/activate  # ou .venv\Scripts\activate sous Windows
pip install -r requirements-dev.txt
cp .env.example .env
uvicorn app.main:app --reload
```

Nécessite une instance PostgreSQL (voir
`docker-compose.yml` du dépôt `smartrecruit-backend` pour lancer toute la stack).

## LLM (OpenRouter)

| Variable | Défaut | Rôle |
|---|---|---|
| `OPENROUTER_API_KEY` | _(vide)_ | clé OpenRouter ; sans clé, les endpoints IA répondent `503` |
| `OPENROUTER_MODEL` | `anthropic/claude-haiku-5.5` | modèle utilisé pour toutes les tâches |
| `OPENROUTER_BASE_URL` | `https://openrouter.ai/api/v1` | |
| `LLM_TIMEOUT_SECONDS` | `9` | délai max d'un appel LLM |
| `MATCHING_TIMEOUT_SECONDS` | `14` | délai max de l'évaluation LLM du matching (réponse sous 15 s) |

Garde-fous anti-hallucination appliqués à **toute** sortie du LLM :
- la réponse doit être un JSON conforme à un schéma Pydantic strict (champs inconnus refusés),
  sinon `502` — aucune valeur de repli n'est inventée ;
- les valeurs extraites sont confrontées à la source (une compétence, une entreprise ou un
  établissement absent du texte du CV est écarté).

## Tests

```bash
pytest
```

## Structure

```
app/
  routers/     # endpoints REST par module IA (cv_parsing, job_analysis, matching, recommendation, skill_gap)
  services/     # logique métier / pipelines IA
  schemas/      # modèles Pydantic (dont MatchScore explicable)
  core/         # configuration (env vars)
  main.py       # point d'entrée FastAPI
tests/
```

## Endpoints

- `GET /api/v1/ai/ping` — health check
- `POST /api/v1/ai/cv-parsing/parse` — IA-01 : multipart, champ `file` (PDF, 5 Mo max) ->
  `CVParsingResult`. Fichier invalide (type, vide, trop gros, corrompu, scanné sans texte) -> `422`.
  Champ optionnel `candidateId` : le résultat est stocké dans `ai_db` (table `parsed_cvs`) et lié au
  candidat ; un PDF identique (SHA-256) déjà analysé est renvoyé sans nouvel appel au LLM.
- `GET /api/v1/ai/cv-parsing/candidates/{id}` — dernier CV analysé du candidat (`404` sinon)
- `DELETE /api/v1/ai/candidates/{id}/data` — effacement RGPD de toutes les données IA du candidat
  (`204`, idempotent — `DATA_MODEL.md` §4.6)
- `POST /api/v1/ai/job-analysis/analyze` — IA-02 : `{ "jobId", "title"?, "description" }` ->
  `JobAnalysisResult` extrait par le LLM. Le résultat est stocké (table `job_analyses`) pour le
  matching, la recommandation et le skill gap : à appeler à chaque publication / modification d'offre.
- `POST /api/v1/ai/matching/score` — IA-03 : `{ "cvId", "jobId" }` (`cvId` = identifiant du candidat
  dont le CV est stocké) -> `MatchScore`. Le LLM note chaque critère, le code calcule le score
  pondéré (compétences 0,5 · expérience 0,3 · formation 0,2) et les contributions. `404` si le CV ou
  l'offre n'a pas été analysé, `422` si aucun critère n'est évaluable, `502`/`503` côté LLM.
- `GET /api/v1/ai/recommendation/candidates/{id}/jobs` — IA-04 : `{ "recommendations": [{ "jobId", "score" }] }`
  (`DATA_MODEL.md` §4.7), 10 offres au plus classées par le LLM parmi les offres analysées
  (présélection des 40 meilleures par recouvrement de compétences). Tout `jobId` inconnu renvoyé
  par le LLM est rejeté. Liste vide si le candidat n'a pas de CV analysé.
- `GET /api/v1/ai/skill-gap/candidates/{id}/jobs/{job_id}` — IA-05 : `{ "missingSkills": [...] }`,
  compétences requises par l'offre absentes du profil, selon le LLM (équivalences prises en compte,
  ex. Postgres = PostgreSQL). Toute compétence qui ne figure pas dans l'offre est rejetée.

Les erreurs suivent le format commun aux 3 dépôts (`DATA_MODEL.md` §1) :
`{ "timestamp", "status", "error", "message", "path" }`.

