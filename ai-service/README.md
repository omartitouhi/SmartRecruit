# SmartRecruit — AI Service

Service IA dédié : CV Parsing, Job Analysis, Matching (embeddings + similarité sémantique),
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
- PostgreSQL + `pgvector` (stockage des embeddings)
- Port par défaut : `8000`

## Lancer en local

```bash
python -m venv .venv
source .venv/bin/activate  # ou .venv\Scripts\activate sous Windows
pip install -r requirements-dev.txt
cp .env.example .env
uvicorn app.main:app --reload
```

Nécessite une instance PostgreSQL avec l'extension `pgvector` (voir
`docker-compose.yml` du dépôt `smartrecruit-backend` pour lancer toute la stack).

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
- `POST /api/v1/ai/cv-parsing/parse` — IA-01
- `POST /api/v1/ai/job-analysis/analyze` — IA-02
- `POST /api/v1/ai/matching/score` — IA-03
- `GET /api/v1/ai/recommendation/candidates/{id}/jobs` — IA-04
- `GET /api/v1/ai/skill-gap/candidates/{id}/jobs/{job_id}` — IA-05

_Toutes les routes métier renvoient 501 pour l'instant — à implémenter au fil des sprints
(voir Roadmap, Sprint 4-5 : Matching, score, recommandations)._
