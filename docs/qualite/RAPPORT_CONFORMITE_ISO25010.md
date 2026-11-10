# Rapport de conformité ISO/IEC 25010 — SmartRecruit

> Toutes les valeurs ci-dessous viennent d'**exécutions réelles** du 9 et 10 novembre 2026 sur la stack Docker Compose
> (Windows 11, Docker Desktop) et de la CI GitHub Actions (Ubuntu). Chaque ligne renvoie au fichier de preuve dans
> `quality/reports/`. Reproduire : `bash quality/run-all.sh` ([guide](GUIDE_INSTALLATION.md)).

## Synthèse

| # | Caractéristique | Exigence mesurable | Outil(s) | Résultat | Verdict |
|---|---|---|---|---|---|
| 6.1 | Aptitude fonctionnelle | Fonctionnalités du §4 implémentées et exactes | JUnit/Spring Test, pytest, Vitest, httpx (API), Playwright | 1 221 tests unitaires/intégration, 146/146 vérifications API, 54/54 tests E2E | ✅ |
| 6.2 | Efficacité de performance | p95 < 500 ms à 20 utilisateurs simultanés, < 1 % d'erreurs | k6 | p95 = **321 ms**, 0,00 % d'erreurs, 73 req/s | ✅ |
| 6.3 | Compatibilité | ≥ 2 navigateurs ; coexistence avec un système tiers | Playwright | Chromium, Edge, WebKit (Safari), Chrome mobile ; API LLM externe OpenRouter interchangeable | ✅ (Firefox : non exécutable sur le poste, voir §6.3) |
| 6.4 | Facilité d'utilisation | Parcours validés, accessibilité de base | Playwright + axe-core (WCAG 2.1 A/AA) | 3 parcours de rôle validés, **0 violation** WCAG sur 6 pages | ✅ |
| 6.5 | Fiabilité | Pas de perte de données, reprise correcte après panne | pytest + arrêt réel de conteneurs | 8/8 après correction de **2 défauts trouvés par ces tests** | ✅ |
| 6.6 | Sécurité | Authentification robuste, RBAC, pas de faille OWASP connue | Bandit, Semgrep, pip-audit, npm audit, Trivy, OWASP ZAP, tests RBAC | SAST 0 ; SCA **179 → 0** CVE ; DAST 0 échec | ✅ |
| 6.7 | Maintenabilité | Couches, complexité maîtrisée, couverture mesurée | JaCoCo, pytest-cov, Vitest v8, lizard, ruff, oxlint | Couverture des lignes 95,6 % (Java), 97,2 % (IA), 92,8 % (front) ; CCN moyen 2,2 | ✅ |
| 6.8 | Portabilité | Installation reproductible sur une autre machine | Docker Compose, GitHub Actions, Kubernetes sur VM Azure | Windows (dev) → Ubuntu (CI) → VM Azure (Kubernetes) avec tests de fumée verts | ✅ |

## 6.1 Aptitude fonctionnelle

**Exigence** : toutes les fonctionnalités minimales (rôles, entités et CRUD, workflow, tableau de bord, recherche, audit,
règles métier) sont implémentées et produisent le résultat attendu.

| Couche | Outil | Volume | Résultat | Preuve |
|---|---|---|---|---|
| Unitaires + intégration backend (API → base H2) | JUnit 5, Spring MockMvc | 686 tests, 6 services | 686 réussis, 0 échec | `quality/reports/unit/<service>/TEST-*.xml` |
| Unitaires service IA | pytest + TestClient | 137 tests | 137 réussis | `quality/reports/unit/ai-service-junit.xml` |
| Unitaires frontend (composants, pages) | Vitest + Testing Library | 398 tests, 32 fichiers | 398 réussis | `quality/reports/unit/frontend-junit.xml` |
| API de bout en bout (gateway + nginx, vraie base PostgreSQL) | script httpx `backend/e2e/e2e_full.py` | 146 vérifications | **146 OK, 0 échec** | `quality/reports/api/e2e-console.txt` |
| E2E interface | Playwright | 18 scénarios × 4 navigateurs | **54 réussis, 0 échec** | `quality/reports/e2e-ui/html/index.html`, `junit.xml` |

Les 9 règles métier `RG-01` à `RG-09` ont chacune au moins un test automatisé
([matrice](MATRICE_TRACABILITE.md)).

## 6.2 Efficacité de performance

**Exigence** : temps de réponse p95 < 500 ms avec 20 utilisateurs simultanés, taux d'erreur < 1 %.

**Test** : `quality/perf/load-test.js` (k6 1.0.0), 20 utilisateurs virtuels pendant 1 minute, mélange réaliste :
recherche filtrée d'offres, détail d'une offre, mes candidatures (candidat) ; statistiques d'entreprise et liste des
offres (recruteur) ; journal d'audit paginé (admin). Les seuils sont codés dans le script : k6 échoue s'ils ne sont
pas tenus.

| Mesure (campagne finale, 10/11/2026) | Valeur |
|---|---|
| Requêtes | 4 529 (73,4 req/s) |
| Durée moyenne / médiane | 100,0 ms / 59,8 ms |
| p90 / **p95** / max | 215,5 ms / **321,4 ms** / 1 117,8 ms |
| Taux d'erreur HTTP | **0,00 %** |
| Contrôles fonctionnels réussis | 100 % |
| Seuils `p(95)<500`, `rate<0.01`, `checks>0.99` | **OK** |

Première mesure (09/11, avant corrections) : p95 = 184,1 ms, 0,02 % d'erreurs (1 délai réseau Docker sur 5 441
requêtes). Preuve : `quality/reports/perf/k6-console.txt`, `k6-summary.json`.

## 6.3 Compatibilité

**Exigence** : fonctionnement sur au moins 2 navigateurs et coexistence avec un système tiers.

- **Multi-navigateurs** : la même suite Playwright tourne sur **Chromium**, **Microsoft Edge 157** (installé),
  **WebKit** (moteur de Safari) et **Chrome mobile** (émulation Pixel 7, écran tactile). Résultat : 54/54 (les 18 tests
  d'accessibilité ne tournent que sur Chromium). Preuve : `quality/reports/e2e-ui/html/index.html` (colonne projet).
- **Firefox** : le binaire Firefox de Playwright ne démarre pas sur le poste Windows de l'équipe
  (`browserType.launch: spawn UNKNOWN`, exécutable bloqué par le système). Conformément à la règle d'or, il est
  **documenté comme non exécutable** et non simulé ; procédure pour une autre machine : `PW_FIREFOX=1 npx playwright test`.
- **Coexistence avec un système tiers** : le service IA appelle un LLM externe (OpenRouter, API compatible OpenAI) quand
  `AI_ENGINE=llm`, et bascule sur un moteur à règles local sinon. `ai-service/tests/test_llm_client.py` vérifie le
  contrat avec l'API externe (réponse JSON validée par schéma, réponse non conforme, échec ou délai dépassé → erreur
  contrôlée) et `test_skill_gap.py` la traduction en 502/503 pour l'appelant. Le
  contrat d'API REST est vérifié par les 146 vérifications API (codes HTTP et structure JSON).

## 6.4 Facilité d'utilisation

**Exigence** : parcours utilisateur validés, accessibilité de base.

- **Parcours validés automatiquement** (Playwright, 4 navigateurs) : connexion par le formulaire des 3 rôles, message
  clair en cas de mauvais mot de passe, redirection vers la connexion pour une page protégée, page 404 lisible,
  recherche filtrée, détail d'une offre, suivi des candidatures, tableau de bord recruteur, classement des candidats,
  journal d'audit, gestion des utilisateurs, refus d'accès `/forbidden` pour un rôle non autorisé.
- **Accessibilité** : audit **axe-core 4.10** (règles WCAG 2.0/2.1 niveaux A et AA) sur 6 pages (connexion,
  inscription, recherche d'offres, candidatures, tableau de bord recruteur, journal d'audit) : **0 violation, toutes
  sévérités confondues**. Preuve : `quality/reports/a11y/*.json` (tableaux vides), test `a11y.spec.ts`.
- **Mobile** : les mêmes parcours passent en émulation Pixel 7 (menu repliable, mise en page étroite).
- Conception : libellés visibles sur chaque champ (les tests s'appuient sur `getByLabel`), lien « Aller au contenu »,
  messages d'erreur sans jargon.

## 6.5 Fiabilité

**Exigence** : comportement stable en cas d'erreur ou de panne, sans perte de données, avec reprise correcte.

**Test** : `quality/reliability/test_reliability.py` provoque de **vraies pannes** (`docker compose stop/start/restart`).

| Scénario | Attendu | Résultat final |
|---|---|---|
| IA arrêtée : création d'offre | 201, l'offre reste valide | ✅ |
| IA arrêtée : score de compatibilité | 502/503 propre, sans trace technique | ✅ |
| IA arrêtée : candidature | 201 enregistrée, score `null` | ✅ |
| IA redémarrée | le score de l'offre créée pendant la panne redevient disponible | ✅ reprise en 46 à 68 s |
| Transition de statut interdite | 400, état inchangé en base | ✅ |
| Double candidature | 409, aucun doublon | ✅ |
| Service candidatures arrêté | erreur 5xx **en moins de 15 s**, puis reprise sans perte de l'état validé | ✅ reprise en 15 à 17 s |
| PostgreSQL redémarré | aucune perte (offre, candidature et son statut) | ✅ reprise immédiate |

**Défauts trouvés par ces tests puis corrigés** (première exécution : 5 réussis, 3 échecs) :

1. *Analyse IA perdue pendant une panne* : une offre créée pendant l'arrêt de l'IA n'était jamais analysée, donc
   jamais notée (503 permanent même après la reprise). Correction : `JobAnalysisListener` (job-service) garde les
   analyses échouées et les relance chaque minute ; test unitaire `JobAnalysisListenerTest`.
2. *Gateway bloqué sur un service arrêté* : la requête restait suspendue plus de 30 s (pas de délai de connexion).
   Correction : délai de connexion de 3 s dans `GatewayConfig` (`gateway.connect-timeout`).
3. Le 3e échec venait du test lui-même (il n'attendait que l'un des deux services après le redémarrage de la base) ;
   le test a été corrigé.

Résultat final : **8/8**. Preuve : `quality/reports/reliability/junit.xml`, `pytest-console.txt`.

## 6.6 Sécurité

**Exigence** : authentification robuste, contrôle d'accès par rôle, absence de failles connues (OWASP Top 10).

| Couche | Outil | Résultat | Preuve |
|---|---|---|---|
| SAST Python | Bandit 1.8.6 | **0 problème** (1 612 lignes) | `security/bandit.txt` |
| SAST Java / TypeScript / Python / secrets | Semgrep 1.140 (`p/owasp-top-ten`, `p/java`, `p/typescript`, `p/python`, `p/secrets`) | **0 finding**, 355 règles, 495 fichiers | `security/semgrep.txt`, `semgrep.json` |
| SCA Python | pip-audit 2.9 | **82 → 0** vulnérabilités | `security/pip-audit-avant.txt`, `pip-audit.txt` |
| SCA npm | npm audit | **1 haute → 0** (follow-redirects) | `security/npm-audit-avant.txt`, `npm-audit.txt` |
| SCA images (dépendances réellement embarquées) | Trivy 0.58.1, 8 images | **179 → 0** (dont 24 critiques : Tomcat, Jackson, jsoup, starlette, pypdf…) | `security/trivy-resume.txt`, `trivy-avant/`, `trivy/` |
| DAST | OWASP ZAP 2.16.1 baseline | **0 échec**, 59 contrôles réussis ; 5 alertes moyennes → 3 acceptées | `security/zap-avant/`, `security/zap/zap-frontend.html` |
| Contrôle d'accès | tests RBAC de chaque service, e2e API, Playwright | 403 pour un rôle non autorisé, 401 sans jeton, `/forbidden` côté interface | `RbacIntegrationTest` (×5), `e2e-console.txt` (section « Sécurité et accès ») |
| Anti-force brute | `LoginIntegrationTest`, `RateLimitingIntegrationTest` + exécution réelle | verrouillage après 5 échecs ; 429 après 10 requêtes / 5 min / IP | `security/rate-limit-429.txt` |

**Corrections apportées** : Spring Boot 4.1.1 → 4.1.2 (Tomcat 11.0.27, Jackson 2.21.7 / 3.1.7), jsoup 1.18.3 → 1.24.1,
Spring Cloud 2025.1.4, FastAPI 0.162 / starlette 1.7, pypdf 6.21.4, python-multipart 0.0.32, python-dotenv 1.2.4,
prometheus-fastapi-instrumentator 8.1 ; pip retiré de l'image d'exécution IA ; en-têtes nginx ajoutés
(`Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`,
`Cross-Origin-*`, `server_tokens off`). Tous les tests ont été rejoués après ces mises à jour (1 221 + 146 + 54 + 8).

**Alertes ZAP acceptées (risque résiduel)** : `style-src 'unsafe-inline'` (styles en ligne de React), `img-src https:
http:` (logos d'entreprise hébergés ailleurs), absence de SRI sur Google Fonts (feuille générée dynamiquement par
Google, SRI impossible). Les alertes faibles restantes concernent l'isolation Spectre (COEP non activé pour garder
Google Fonts).

**Correspondance OWASP Top 10 (2021)** : A01 contrôle d'accès (RBAC + identité signée HMAC, tests 401/403) ; A02
cryptographie (BCrypt, CV chiffrés, JWT signés) ; A03 injection (JPA paramétré, assainissement XSS jsoup, Semgrep) ;
A05 mauvaise configuration (ZAP, en-têtes) ; A06 composants vulnérables (pip-audit, npm audit, Trivy) ; A07
authentification (verrouillage, rate limiting) ; A09 journalisation (journal d'audit des actions sensibles).

## 6.7 Maintenabilité

**Exigence** : code structuré en couches, complexité maîtrisée, couverture de tests mesurée.

| Composant | Lignes couvertes | Branches couvertes | Outil |
|---|---|---|---|
| api-gateway | 91,4 % | 81,8 % | JaCoCo 0.8.14 |
| auth-service | 97,2 % | 89,1 % | JaCoCo |
| user-service | 93,5 % | 87,7 % | JaCoCo |
| job-service | 96,6 % | 90,5 % | JaCoCo |
| application-service | 97,7 % | 94,4 % | JaCoCo |
| dashboard-service | 96,5 % | 86,0 % | JaCoCo |
| **Backend Java (total)** | **95,6 %** | **88,9 %** | JaCoCo |
| ai-service | 97,2 % (instructions) | 91,2 % | pytest-cov |
| frontend | 92,8 % | 87,0 % | Vitest (v8) |

Rapports HTML : `quality/reports/coverage/<composant>/index.html`.

- **Complexité** (lizard 1.17, Java + Python + TypeScript, 13 590 lignes) : complexité cyclomatique moyenne **2,2** par
  fonction ; 5 fonctions sur 862 (0,6 %) dépassent 15, dont 2 dans le code de maquette du frontend (`api/mock`).
  Preuve : `quality/reports/maintainability/lizard-complexite.txt`.
- **Lint** : ruff (Python) « All checks passed », oxlint (TypeScript) 0 avertissement — bloquants en CI.
- **Structure** : couches `controller/service/repository/model/dto` dans chaque service ; un service par domaine ; contrats
  documentés (`DATA_MODEL.md`, `API_CONTRACTS.md`).

## 6.8 Portabilité

**Exigence** : installation reproductible sur une autre machine (script ou conteneur).

- **Conteneurs** : chaque composant a son `Dockerfile` ; `docker compose up -d --build` installe toute la stack. La
  campagne finale a été jouée sur une stack entièrement reconstruite à partir du dépôt.
- **Autre machine, autre OS** : la CI GitHub Actions (Ubuntu) rejoue tous les tests, valide les manifestes
  Kubernetes/Helm/Terraform/Ansible, construit les 8 images, **déploie sur une VM Azure** (Ubuntu, Kubernetes kind via
  Ansible) et lance des tests de fumée. Exécution
  [42530810230](https://github.com/omartitouhi/SmartRecruit/actions/runs/42530810230) (commit `3b4cd95`) : 22 jobs sur
  22 réussis, dont « 4 · Déploiement VM Azure » et « 5 · Tests de fumée ».
- **Script d'installation documenté** : [GUIDE_INSTALLATION.md](GUIDE_INSTALLATION.md) et `quality/run-all.sh install`.

## Limites et éléments non vérifiés

- Firefox non exécutable sur le poste de l'équipe (voir 6.3).
- La CI a été vérifiée sur le commit `3b4cd95` ; les corrections de cette campagne (versions, nginx, fiabilité) ont été
  testées localement par toutes les couches ci-dessus et seront vérifiées en CI au prochain push.
- Tests de charge sur un poste de développement (toute la stack sur une seule machine) : les valeurs absolues dépendent
  du matériel ; le seuil est tenu avec une marge (321 ms contre 500 ms).
