# Répartition des tests entre les 4 membres

Chaque membre est responsable d'un bloc de caractéristiques ISO/IEC 25010 : il **ré-exécute** ses couches de test,
**vérifie** que les preuves de `quality/reports/` sont à jour et cohérentes avec la
[matrice de traçabilité](MATRICE_TRACABILITE.md), et **présente** ses résultats en soutenance.
Les commandes se lancent depuis la racine du dépôt (Git Bash sous Windows), application démarrée
(`docker compose up -d --build`) sauf pour `unit`, `sast` et `maintainability`.

| | Membre 1 | Membre 2 | Membre 3 | Membre 4 |
|---|---|---|---|---|
| Nom | *(à compléter)* | *(à compléter)* | *(à compléter)* | *(à compléter)* |
| Thème | Logique métier et API | Interface utilisateur | Performance, fiabilité, portabilité | Sécurité et qualité de code |
| ISO 25010 (§6) | 6.1 Aptitude fonctionnelle, 6.7 Maintenabilité (couverture) | 6.1 (E2E), 6.3 Compatibilité, 6.4 Facilité d'utilisation | 6.2 Efficacité de performance, 6.5 Fiabilité, 6.8 Portabilité | 6.6 Sécurité, 6.7 Maintenabilité (analyse statique) |
| Couches (§7) | Unitaires, intégration, API | Fonctionnels/E2E, compatibilité, unitaires frontend | Performance, fiabilité, portabilité | SAST, SCA, DAST, qualité/maintenabilité |

## Membre 1 — Logique métier et API

| Couche | Outil | Emplacement | Commande | Preuve |
|---|---|---|---|---|
| Unitaires + intégration (backend Java, 6 services) | JUnit 5, Spring Test/MockMvc, JaCoCo | `backend/*/src/test` | `bash quality/run-all.sh unit` | `quality/reports/unit/<service>/`, `coverage/<service>/` |
| Unitaires + intégration (service IA) | pytest, TestClient, pytest-cov | `ai-service/tests` | `cd ai-service && pytest -q` | `quality/reports/unit/ai-service-junit.xml`, `coverage/ai-service/` |
| Tests API (contrat HTTP de bout en bout) | httpx | `backend/e2e/e2e_full.py` | `bash quality/run-all.sh api` | `quality/reports/api/e2e-console.txt` (155 OK) |

À vérifier : EF-01 à EF-06 et **les 9 règles métier RG-01 à RG-09** (un test nommé par règle dans la matrice §2),
couverture des lignes (Java 95,7 %, IA 97,2 %).

## Membre 2 — Interface utilisateur

| Couche | Outil | Emplacement | Commande | Preuve |
|---|---|---|---|---|
| Unitaires frontend | Vitest (+ couverture v8) | `frontend/src/**/*.test.tsx` | `cd frontend && npm test` | `quality/reports/unit/frontend-junit.xml`, `coverage/frontend/` |
| Fonctionnels / E2E (parcours des 3 rôles) | Playwright | `quality/e2e-ui/tests/journeys.spec.ts`, `auth.setup.ts` | `bash quality/run-all.sh ui` | `quality/reports/e2e-ui/html/index.html`, `junit.xml` |
| Compatibilité multi-navigateurs | Playwright (Chromium, Edge, WebKit, Chrome mobile) | `quality/e2e-ui/playwright.config.ts` | idem | 62 réussis ; Firefox documenté non exécutable |
| Accessibilité de base | axe-core (WCAG 2.1 A/AA) | `quality/e2e-ui/tests/a11y.spec.ts` | idem | `quality/reports/a11y/*.json` (0 violation) |

À vérifier : recette utilisateur (parcours candidat, recruteur, admin), refus d'accès `/forbidden`.

## Membre 3 — Performance, fiabilité, portabilité

| Couche | Outil | Emplacement | Commande | Preuve |
|---|---|---|---|---|
| Performance (20 utilisateurs, p95 < 500 ms) | k6 | `quality/perf/load-test.js` | `bash quality/run-all.sh perf` | `quality/reports/perf/k6-console.txt`, `k6-summary.json` |
| Fiabilité (pannes réelles de conteneurs, 8 scénarios) | pytest + Docker | `quality/reliability/test_reliability.py` | `bash quality/run-all.sh reliability` | `quality/reports/reliability/junit.xml`, `pytest-console.txt` |
| Compatibilité système tiers (API LLM externe) | pytest | `ai-service/tests/test_llm_client.py` | `cd ai-service && pytest -q tests/test_llm_client.py` | idem `unit/ai-service-junit.xml` |
| Portabilité (installation reproductible) | Docker Compose, GitHub Actions, Kubernetes | `docker-compose.yml`, `.github/workflows/pipeline.yml`, `deploy/` | `bash quality/run-all.sh install` (idéalement sur **une autre machine** en suivant le [guide](GUIDE_INSTALLATION.md)) | exécutions CI liées dans la matrice (EQ-08) |

À vérifier : seuils k6 `OK`, aucune perte de données après panne (8/8), installation par un tiers.

## Membre 4 — Sécurité et qualité de code

| Couche | Outil | Emplacement | Commande | Preuve |
|---|---|---|---|---|
| SAST | Bandit, Semgrep | code de production | `bash quality/run-all.sh sast` | `quality/reports/security/bandit.txt`, `semgrep.txt` |
| SCA (CVE des dépendances) | pip-audit, npm audit, Trivy (8 images) | requirements, lockfile, images | `bash quality/run-all.sh sca` | `security/pip-audit*.txt`, `npm-audit*.txt`, `trivy-resume.txt` (avant/après) |
| DAST | OWASP ZAP baseline | application en cours d'exécution | `bash quality/run-all.sh dast` | `quality/reports/security/zap/` |
| Contrôle d'accès, anti-force brute | JUnit (`RbacIntegrationTest`, `RateLimitingIntegrationTest`), httpx | `backend/*/src/test`, `backend/e2e` | inclus dans `unit` / `api` | `security/rate-limit-429.txt` |
| Qualité / maintenabilité | lizard, ruff, oxlint | — | `bash quality/run-all.sh maintainability` | `quality/reports/maintainability/` |

À vérifier : correspondance OWASP Top 10 (A01, A03, A05, A06, A07), 0 vulnérabilité, CCN moyen, lint à 0.

## Règles communes

- **Règle d'or** : aucun résultat sans exécution réelle ; après chaque ré-exécution, mettre à jour la ligne
  correspondante de la matrice et du [rapport de conformité](RAPPORT_CONFORMITE_ISO25010.md). Un outil qui ne
  s'exécute pas sur le poste est documenté comme tel (procédure + « résultat non disponible »).
- Avant la soutenance, un membre lance la campagne complète `bash quality/run-all.sh` et archive la sortie dans
  `quality/reports/campagne-finale-console.txt`.
- Chaque membre relit les tests d'un autre (1→2→3→4→1) pour qu'aucune couche n'ait un seul regard.
