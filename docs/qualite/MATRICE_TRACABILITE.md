# Matrice de traçabilité — exigence → test(s) → outil(s) → résultat

Résultats issus de la campagne finale du 11/11/2026 (`quality/reports/campagne-finale-console.txt` : toutes les étapes de `run-all.sh` réussies
en un seul passage). ✅ = vérifié par une exécution réelle dont la preuve est citée.
Abréviations des preuves : `R/` = `quality/reports/`.

## 1. Exigences fonctionnelles (cahier des charges §4)

| Id | Exigence | Test(s) | Outil(s) | Résultat | Preuve |
|---|---|---|---|---|---|
| EF-01 | Authentification, 3 rôles aux droits différenciés | `LoginIntegrationTest`, `RbacIntegrationTest` (×5 services), `GatewayAuthenticationIntegrationTest` ; e2e API « Sécurité et accès » ; Playwright `auth.setup.ts` (connexion des 3 rôles), « accès refusé à l'espace administrateur » | JUnit/MockMvc, httpx, Playwright | ✅ | `R/unit/`, `R/api/e2e-console.txt`, `R/e2e-ui/html` |
| EF-02 | ≥ 4 entités liées, CRUD complet | 6 entités avec les 4 opérations ([conception §3](RAPPORT_CONCEPTION.md#3-modèle-de-données)) ; tests d'intégration des contrôleurs, dont `JobDeleteIntegrationTest` (suppression d'un brouillon, 409 sinon) et `NotesIntegrationTest.onlyTheAuthorEditsANote` ; e2e API « suppression d'un brouillon », « modification d'une note » ; Vitest « deleting a draft offer », « editing one's own note » | JUnit/MockMvc, httpx, Vitest | ✅ 693 + 155 | `R/unit/`, `R/api/e2e-console.txt` |
| EF-03 | Workflow à statuts avec règles de transition | `StatusChangeIntegrationTest.transitionTableIsEnforced`, `JobStatusIntegrationTest` ; fiabilité `test_transition_interdite_rejetee_sans_modifier_l_etat` ; Playwright « suivi de ses candidatures » | JUnit, pytest, Playwright | ✅ | `R/unit/application-service/`, `R/reliability/junit.xml` |
| EF-04 | Tableau de bord (indicateurs agrégés, filtres) | `GlobalStatsIntegrationTest`, `CompanyStatsIntegrationTest.periodIsForwardedForTheApplicationFiguresAndChecked`, `InternalStatsIntegrationTest.periodKeepsOnlyTheApplicationsMadeInIt` ; e2e « filtre par periode » ; Playwright « tableau de bord filtré par période » ; e2e « Tableau de bord et administration », « Pilotage enrichi » ; Playwright « tableau de bord avec indicateurs » ; k6 (statistiques sous charge) | JUnit, httpx, Playwright, k6 | ✅ | `R/unit/dashboard-service/`, `R/perf/` |
| EF-05 | Recherche / filtrage sur une entité à fort volume | `JobSearchIntegrationTest`, `JobEnrichmentIntegrationTest.searchFiltersOnWorkModeAndMinimumSalary`, `JobSearchPage.test.tsx` ; Playwright « recherche d'offres filtrée par ville », « recherche dans un fort volume d'offres » (> 1 000 résultats) ; k6 (recherche paginée sur 3 068 offres, 20 utilisateurs) | JUnit, Vitest, Playwright, k6 | ✅ | `R/unit/job-service/`, `R/e2e-ui/html` |
| EF-06 | Journal d'audit des actions sensibles | `AdminAuditLogIntegrationTest`, `AuditIngestionIntegrationTest` ; e2e « journal d'audit » (CV_VIEWED, APPLICATION_STATUS_CHANGED, USER_STATUS_CHANGED, JOB_MODERATED, filtre par action) ; Playwright « journal d'audit : qui a fait quoi, quand » | JUnit, httpx, Playwright | ✅ | `R/unit/dashboard-service/`, `R/api/e2e-console.txt` |

## 2. Règles métier (détail dans le [rapport de conception §5](RAPPORT_CONCEPTION.md#5-règles-métier-explicites-et-testables))

| Id | Règle | Test(s) | Outil | Résultat |
|---|---|---|---|---|
| RG-01 | Email de compte unique | `RegisterIntegrationTest.registerWithExistingEmailReturns409` | JUnit | ✅ |
| RG-02 | Un recruteur = une entreprise | e2e « seconde entreprise du meme recruteur refusee (409) » | httpx | ✅ |
| RG-03 | Transitions de candidature contrôlées | `StatusChangeIntegrationTest.transitionTableIsEnforced` ; `test_transition_interdite_rejetee_sans_modifier_l_etat` | JUnit, pytest | ✅ |
| RG-04 | Une candidature active par offre | `ApplyIntegrationTest.canApplyAgainAfterWithdrawing`, `sameJobFromAnotherCandidateIsNotADuplicate` ; `test_double_candidature_rejetee_pas_de_doublon` | JUnit, pytest | ✅ |
| RG-05 | Postuler : offre publiée, profil complet, CV | `ApplyIntegrationTest.unpublishedJobGets409`, `incompleteProfileGets422` | JUnit | ✅ |
| RG-06 | Anti-force brute | `LoginIntegrationTest.fiveFailuresWithin15MinutesLockTheAccount`, `RateLimitingIntegrationTest` ; 429 réel pendant la campagne | JUnit, httpx | ✅ `R/security/rate-limit-429.txt` |
| RG-07 | Un admin ne se désactive pas lui-même | `AdminUserIntegrationTest.anAdminCannotDisableThemself` | JUnit | ✅ |
| RG-08 | Salaire min ≤ max, clôture à la date limite | `JobEnrichmentIntegrationTest.patchChecksTheSalaryRangeAgainstTheStoredBound`, `expiredOffersAreClosedAndCannotBeReopened` | JUnit | ✅ |
| RG-09 | Workflow d'offre, offre modérée figée | `JobStatusIntegrationTest.forbiddenTransitionsGet400AndNothingChanges`, `moderatedOfferIsOutOfTheRecruitersHands`, `JobUpdateIntegrationTest.closedAndModeratedOffersGet409` | JUnit | ✅ |

## 3. Exigences de qualité ISO/IEC 25010 (cahier des charges §6)

| Id | Caractéristique | Exigence mesurable | Test(s) | Outil(s) | Résultat obtenu | Preuve |
|---|---|---|---|---|---|---|
| EQ-01 | Aptitude fonctionnelle | §4 implémenté et exact | Unitaires/intégration, API, E2E | JUnit, pytest, Vitest, httpx, Playwright | 1 233 tests unitaires ✅ ; API 155/155 ✅ ; E2E 62/62 ✅ | `R/unit/`, `R/api/`, `R/e2e-ui/` |
| EQ-02 | Efficacité de performance | p95 < 500 ms à 20 utilisateurs, erreurs < 1 % | `quality/perf/load-test.js` | k6 1.0.0 | p95 **349,6 ms** sur 3 068 offres, 0,00 % d'erreurs, 4 463 requêtes ✅ | `R/perf/k6-console.txt`, `k6-summary.json` |
| EQ-03 | Compatibilité | ≥ 2 navigateurs ; système tiers | `journeys.spec.ts` sur 4 projets ; `test_llm_client.py` (API OpenRouter) | Playwright, pytest | Chromium, Edge, WebKit, Chrome mobile ✅ ; Firefox **non exécutable** sur le poste (documenté) | `R/e2e-ui/html` |
| EQ-04 | Facilité d'utilisation | Parcours validés, accessibilité de base | `journeys.spec.ts`, `a11y.spec.ts` (6 pages) | Playwright, axe-core (WCAG 2.1 A/AA) | Parcours ✅ ; **0 violation** d'accessibilité ✅ | `R/a11y/*.json` |
| EQ-05 | Fiabilité | Pas de perte de données, reprise après panne | `quality/reliability/test_reliability.py` (8 scénarios, pannes réelles) ; `JobAnalysisListenerTest` | pytest + Docker, JUnit | 8/8 ✅ après correction de 2 défauts trouvés | `R/reliability/` |
| EQ-06a | Sécurité — SAST | Aucune vulnérabilité dans le code | Analyse du code de production | Bandit, Semgrep | 0 ✅ | `R/security/bandit.txt`, `semgrep.txt` |
| EQ-06b | Sécurité — SCA | Aucune CVE connue dans les dépendances | Requirements, lockfile npm, 8 images | pip-audit, npm audit, Trivy | 82 → 0, 1 → 0, 179 → 0 ✅ | `R/security/*-avant*`, `trivy-resume.txt` |
| EQ-06c | Sécurité — DAST | Pas de faille détectable à l'exécution | Scan de l'interface en fonctionnement | OWASP ZAP baseline | 0 échec, 59 contrôles OK ✅ ; 3 alertes moyennes acceptées | `R/security/zap/` |
| EQ-06d | Sécurité — contrôle d'accès | 401 sans jeton, 403 hors rôle | `RbacIntegrationTest`, e2e « Sécurité et accès », Playwright `/forbidden` | JUnit, httpx, Playwright | ✅ | `R/unit/`, `R/api/` |
| EQ-07 | Maintenabilité | Couverture mesurée, complexité maîtrisée, lint | Couverture de toutes les suites ; analyse de complexité | JaCoCo, pytest-cov, Vitest v8, lizard, ruff, oxlint | Lignes : Java 95,7 %, IA 97,2 %, front 92,1 % ; CCN moyen 2,2 ; lint 0 ✅ | `R/coverage/`, `R/maintainability/` |
| EQ-08 | Portabilité | Installation reproductible ailleurs | `docker compose up --build` ; CI Ubuntu + déploiement VM Azure + tests de fumée | Docker, GitHub Actions, Kubernetes | Stack reconstruite et testée ✅ ; CI de la version finale (`ed0779b`) : 20/20 jobs ✅ ; dernier déploiement Azure + fumée (`3b4cd95`) : 22/22 jobs ✅ | [CI finale](https://github.com/omartitouhi/SmartRecruit/actions/runs/42859400550), [déploiement](https://github.com/omartitouhi/SmartRecruit/actions/runs/42530810230/attempts/2) |

## 4. Couches de test du cahier des charges (§7) → outils retenus

| Couche | Outil(s) | Emplacement | Exécution |
|---|---|---|---|
| Tests unitaires | JUnit 5, pytest, Vitest | `backend/*/src/test`, `ai-service/tests`, `frontend/src/**/*.test.tsx` | `run-all.sh unit` |
| Tests d'intégration | Spring Test (MockMvc + H2 + faux services HTTP), FastAPI TestClient | idem | `run-all.sh unit` |
| Tests API | httpx (équivalent Postman/RestAssured) | `backend/e2e/e2e_full.py` | `run-all.sh api` |
| Tests fonctionnels / E2E | Playwright | `quality/e2e-ui` | `run-all.sh ui` |
| Tests de performance | k6 | `quality/perf` | `run-all.sh perf` |
| Tests de fiabilité | pytest + pannes Docker | `quality/reliability` | `run-all.sh reliability` |
| SAST | Bandit, Semgrep | — | `run-all.sh sast` |
| SCA | pip-audit, npm audit, Trivy | — | `run-all.sh sca` |
| DAST | OWASP ZAP | — | `run-all.sh dast` |
| Qualité / maintenabilité | JaCoCo, pytest-cov, Vitest v8, lizard, ruff, oxlint | — | `run-all.sh unit maintainability` |
| Compatibilité | Playwright (Chromium, Edge, WebKit, mobile) | `quality/e2e-ui` | `run-all.sh ui` |
| Portabilité | Docker Compose, GitHub Actions, Kubernetes | `docker-compose.yml`, `.github/workflows`, `deploy/` | `run-all.sh install`, CI |
