# Guide d'installation et d'exécution des tests — SmartRecruit

Ce guide permet à un tiers de reproduire l'installation et **toutes les couches de test** sur sa machine. Les résultats
sont écrits dans `quality/reports/`. Vérifié sous Windows 11 (Git Bash + Docker Desktop) ; la CI le reproduit sous
Linux (Ubuntu, GitHub Actions).

## 1. Prérequis

| Outil | Version testée | Usage |
|---|---|---|
| Git | 2.x | récupérer le dépôt |
| Docker + Docker Compose | Docker Desktop 4.x | toute la stack, k6, Trivy, Semgrep, OWASP ZAP (aucune installation locale de ces outils) |
| Java (JDK) | 21 (25 accepté) | tests Maven (wrapper `mvnw` inclus) |
| Node.js | 20+ | frontend, Playwright |
| Python | 3.10+ (3.12 recommandé) | service IA, tests API, fiabilité, Bandit, pip-audit |

Ports utilisés : 5173 (interface), 8080 (gateway), 8000 (IA), 8081-8085 (services), 5432 (PostgreSQL).

## 2. Installation de l'application

```bash
git clone https://github.com/omartitouhi/SmartRecruit.git
cd SmartRecruit
cp .env.example .env
docker compose up -d --build
python backend/seed/seed_demo.py
```

- Interface : http://localhost:5173 — API : http://localhost:8080 — IA (Swagger) : http://localhost:8000/docs
- Comptes de démonstration (mot de passe `Motdepasse123`) : `candidat@smartrecruit.test`,
  `recruteur@smartrecruit.test` ; administrateur : `ADMIN_EMAIL` / `ADMIN_PASSWORD` du fichier `.env`.
- Le script de données de démo prend environ 10 minutes (analyse de 19 CV) et peut être relancé sans doublon.
- Repartir de zéro : `docker compose down -v`.

## 3. Exécuter les tests

Toutes les couches, dans l'ordre (≈ 30 à 45 minutes, dont le téléchargement des images d'outils au premier lancement) :

```bash
bash quality/run-all.sh
```

Une couche à la fois :

```bash
bash quality/run-all.sh unit
```

| Étape | Couche (cahier des charges §7) | Outil | Rapport |
|---|---|---|---|
| `install` | Portabilité | Docker Compose | console |
| `unit` | Tests unitaires et d'intégration + couverture | JUnit 5 / Spring Test / JaCoCo, pytest / pytest-cov, Vitest / v8 | `quality/reports/unit/`, `quality/reports/coverage/` |
| `api` | Tests API (contrat, codes HTTP) | script httpx `backend/e2e/e2e_full.py` | `quality/reports/api/e2e-console.txt` |
| `ui` | Tests E2E, accessibilité, multi-navigateurs | Playwright + axe-core | `quality/reports/e2e-ui/html/index.html`, `quality/reports/a11y/` |
| `perf` | Performance | k6 | `quality/reports/perf/` |
| `reliability` | Fiabilité (pannes réelles) | pytest + docker compose | `quality/reports/reliability/` |
| `sast` | Analyse statique sécurité | Bandit, Semgrep | `quality/reports/security/` |
| `sca` | Dépendances vulnérables | pip-audit, npm audit, Trivy | `quality/reports/security/` |
| `dast` | Analyse dynamique | OWASP ZAP baseline | `quality/reports/security/zap/` |
| `maintainability` | Complexité, lint | lizard, ruff, oxlint | `quality/reports/maintainability/` |

Les étapes `api`, `ui`, `perf`, `reliability` et `dast` demandent la stack démarrée avec les données de démo (étape
`install`). L'étape `reliability` **arrête et redémarre volontairement** des conteneurs (`ai-service`,
`application-service`, `postgres`) ; elle les remet en marche à la fin, même en cas d'échec.

Commandes équivalentes sans le script (exemples) :

```bash
cd backend/job-service && ./mvnw verify
```

```bash
cd ai-service && pip install -r requirements-dev.txt && pytest --cov=app
```

```bash
cd frontend && npm ci && npm run test:coverage
```

```bash
cd quality/e2e-ui && npm ci && npx playwright install chromium webkit && npx playwright test
```

```bash
pytest quality/reliability -v -s
```

## 4. Limites connues de l'environnement (règle d'or du cahier des charges)

- **Firefox (Playwright)** : le binaire Firefox téléchargé par Playwright ne démarre pas sur le poste Windows de l'équipe
  (`spawn UNKNOWN` : exécutable bloqué par le système). Il est désactivé par défaut ; pour l'exécuter sur une autre
  machine : `PW_FIREFOX=1 npx playwright test`. La compatibilité est prouvée sur Chromium, Microsoft Edge, WebKit
  (moteur de Safari) et Chrome mobile (Pixel 7).
- **Microsoft Edge** : le projet Playwright `edge` utilise l'Edge installé sur la machine ; sous Linux sans Edge,
  lancer `npx playwright test --project=chromium --project=webkit --project=mobile-chrome`.
- **Réseau d'entreprise avec proxy TLS** : `npm audit` peut échouer (`unable to verify the first certificate`) ;
  utiliser `NODE_OPTIONS=--use-system-ca npm audit --omit=dev`.
- **Limitation des connexions** : la connexion et l'inscription sont limitées à 10 requêtes / 5 min / IP. Enchaîner
  plusieurs campagnes de tests à la suite peut donner des 429 ; attendre 5 minutes ou utiliser le script (qui relève
  la limite le temps de la campagne API seulement).
