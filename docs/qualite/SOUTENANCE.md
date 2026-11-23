# Guide de soutenance

Format : **le lead présente tout** (application + 2 couches de test en live), puis **l'enseignant interroge chaque
membre sur les tests dont il est responsable** ([répartition](REPARTITION_TESTS.md)).
Objectif imposé (§8) : démonstration live de l'application **et d'au moins deux couches de test en exécution**.

| Rôle | Nom | Interrogé sur |
|---|---|---|
| Membre 1 — lead (présentation) | *(à compléter)* | vue d'ensemble, architecture, choix, démo ; unitaires, intégration, API, règles métier, couverture |
| Membre 2 | *(à compléter)* | E2E Playwright, compatibilité navigateurs, accessibilité, Vitest |
| Membre 3 | *(à compléter)* | performance k6, fiabilité (pannes), portabilité / CI |
| Membre 4 | *(à compléter)* | SAST, SCA, DAST, contrôle d'accès, maintenabilité |

---

## 1. Préparation (J-1 et 15 min avant)

```bash
docker compose up -d --build
```

```bash
python backend/seed/seed_demo.py
```

```bash
python backend/seed/seed_volume.py
```

- Vérifier http://localhost:5173 (connexion `candidat@smartrecruit.test` / `Motdepasse123`).
- Dans `quality/e2e-ui` : `npm ci` et `npx playwright install chromium webkit` déjà faits ; image k6 déjà téléchargée
  (`docker pull grafana/k6:1.0.0`).
- **Ne pas se connecter plus de 10 fois en 5 minutes** avant la démo (anti-force brute, RG-06) : les tests Playwright
  se connectent 4 fois.
- Onglets ouverts à l'avance : `quality/reports/e2e-ui/html/index.html`, `quality/reports/coverage/job-service/index.html`,
  `quality/reports/security/zap/zap-frontend.html`, [MATRICE_TRACABILITE.md](MATRICE_TRACABILITE.md),
  [RAPPORT_CONFORMITE_ISO25010.md](RAPPORT_CONFORMITE_ISO25010.md).
- **Chaque membre** a ouvert sur son poste (ou sait retrouver) les fichiers de preuve de sa partie et sait relancer
  sa commande (section 4) : l'enseignant peut demander « montrez-moi » ou « relancez-le ».

---

## 2. Présentation du lead (≈ 12 min)

| Temps | Contenu | À montrer |
|---|---|---|
| 0:00 – 1:00 | **Le sujet** : SmartRecruit, plateforme de recrutement (domaine RH) assistée par IA. 3 rôles : candidat, recruteur, admin. | page d'accueil |
| 1:00 – 2:30 | **Architecture** : React → API Gateway (JWT, rate limiting) → 6 microservices Spring Boot + 1 service IA FastAPI, PostgreSQL. Couches `controller/service/repository/model` dans chaque service. Choix justifiés : Spring (sécurité, JPA, tests), FastAPI (écosystème IA, Pydantic), React+TS (typage), Docker/K8s (portabilité). | [DIAGRAMMES.md](DIAGRAMMES.md) |
| 2:30 – 5:30 | **Démo application** (exigences §4) : candidat → recherche filtrée parmi 3 068 offres → détail → score IA expliqué → candidature ; recruteur → tableau de bord filtré par période → classement → changement de statut (workflow) → modification d'une note → suppression d'un brouillon ; admin → journal d'audit filtré (qui / quoi / quand) → désactivation d'un compte. | application |
| 5:30 – 7:00 | **Couche live n°1 — E2E Playwright** (commande 3.1) : expliquer pendant l'exécution ce que fait chaque parcours, puis ouvrir le rapport HTML. | navigateur visible |
| 7:00 – 8:30 | **Couche live n°2 — charge k6** (commande 3.2) : 20 utilisateurs, 1 min ; lire à voix haute requêtes, p95, taux d'erreur, seuils `OK`. | console |
| 8:30 – 11:00 | **ISO 25010, une preuve par caractéristique** : tableau de synthèse du rapport de conformité ; insister sur la **traçabilité** (exigence → test → outil → résultat) et sur les **défauts réellement trouvés et corrigés** : 2 défauts de fiabilité, 179 CVE → 0. | rapport + matrice |
| 11:00 – 12:00 | **Organisation** : 4 membres, un bloc ISO par membre ; règle d'or respectée (Firefox documenté non exécutable plutôt que simulé). Annonce : « chacun va répondre sur sa partie ». | [REPARTITION_TESTS.md](REPARTITION_TESTS.md) |

Phrases clés du lead :
- « Aucun chiffre n'est déclaratif : chaque résultat renvoie à un fichier de `quality/reports/` produit par une
  exécution réelle, et toute la campagne se rejoue avec `bash quality/run-all.sh`. »
- « Nos tests ne servent pas qu'à prouver : ils ont trouvé des défauts que nous avons corrigés. »

---

## 3. Commandes live

### 3.1 E2E de l'interface (≈ 40 s)

```bash
cd quality/e2e-ui && npx playwright test --project=chromium --headed
```

Puis : `npx playwright show-report ../reports/e2e-ui/html`.

### 3.2 Test de charge (1 min, depuis la racine, Git Bash)

```bash
docker run --rm -v "$(pwd -W 2>/dev/null || pwd)/quality/perf:/scripts:ro" -v "$(pwd -W 2>/dev/null || pwd)/quality/reports/perf:/reports" grafana/k6:1.0.0 run /scripts/load-test.js
```

### 3.3 Plan B (si une démo échoue)

- Couche rapide : `cd ai-service && pytest -q` (137 tests en quelques secondes) ou `cd frontend && npm test` (403 tests).
- Montrer les rapports déjà générés dans `quality/reports/` (exécutions réelles, horodatées, campagne du 11/11/2026).

---

## 4. Questions par membre

Réponses courtes, chiffrées, toujours terminées par **où est la preuve**. Si une question sort de votre partie :
« c'est la partie de X, mais en bref… » puis passer la parole.

### Membre 1 — Logique métier, unitaires, intégration, API

Commande à savoir relancer : `bash quality/run-all.sh unit` (ou `cd ai-service && pytest -q`), `bash quality/run-all.sh api`.

| Question probable | Réponse |
|---|---|
| Différence entre test unitaire et test d'intégration chez vous ? | Unitaire : une classe isolée (règle de scoring, validation). Intégration : requête HTTP réelle sur le contrôleur via MockMvc → service → repository → base H2, avec les autres services simulés par de faux serveurs HTTP. |
| Pourquoi H2 et pas PostgreSQL en intégration ? | Rapidité et isolation (base neuve à chaque test). Le vrai PostgreSQL est couvert par les 155 tests API sur la stack Docker complète. |
| Combien de tests ? | 693 JUnit (6 services) + 137 pytest (IA) + 403 Vitest (front) = 1 233 ; 155/155 vérifications API. Preuve : `quality/reports/unit/`, `api/e2e-console.txt`. |
| Montrez une règle métier et son test. | RG-03 : `StatusChangeIntegrationTest.transitionTableIsEnforced`, test paramétré qui essaie toutes les transitions ; celles hors table renvoient 400 et l'état ne change pas. RG-04 : double candidature → 409. |
| Comment testez-vous le workflow ? | Table `ApplicationStatus.ALLOWED_TRANSITIONS` : RECEIVED → IN_REVIEW → INTERVIEW → ACCEPTED/REJECTED, retrait WITHDRAWN par le candidat seulement. Chaque changement ajoute un historique et un événement d'audit. |
| Que vérifient les tests API ? | Le contrat : codes HTTP (200/201/400/401/403/404/409/422/429) et structure JSON, en passant par le gateway et nginx comme un vrai client. |
| Pourquoi httpx et pas Postman ? | Le cahier des charges accepte un équivalent : un script Python versionné, rejouable en CI, avec un verdict automatique. |
| Couverture ? | Lignes : Java 95,7 % (91,4 à 97,7 % selon le service), IA 97,2 %, frontend 92,1 %. Rapports HTML : `quality/reports/coverage/`. |
| Une couverture élevée garantit-elle l'absence de bugs ? | Non : elle montre ce qui est exécuté, pas ce qui est vérifié. C'est pour ça qu'on teste chaque règle par une assertion explicite et qu'on ajoute API, E2E et fiabilité. |

### Membre 2 — Interface, E2E, compatibilité, accessibilité

Commande : `bash quality/run-all.sh ui` (ou `cd quality/e2e-ui && npx playwright test`).

| Question probable | Réponse |
|---|---|
| Que testent vos E2E ? | 13 parcours réels : connexion des 3 rôles, mauvais mot de passe, redirection d'une page protégée, 404, recherche filtrée (y compris > 1 000 résultats), détail d'offre, candidatures, tableau de bord filtré par période, classement, journal d'audit, gestion des utilisateurs, refus `/forbidden`. |
| Pourquoi 62 tests et pas 80 dans le junit.xml ? | 13 parcours × 4 navigateurs = 52, + 6 audits d'accessibilité sur Chromium + 4 connexions de préparation (`auth.setup.ts`) = 62. Les 18 « skipped » sont les audits d'accessibilité volontairement non répétés sur les autres navigateurs. |
| Quels navigateurs ? | Chromium, Edge, WebKit (moteur de Safari), Chrome mobile (Pixel 7). Le cahier demande ≥ 2. |
| Et Firefox ? | Le binaire Playwright est bloqué par Windows sur notre poste (`spawn UNKNOWN`). Règle d'or : documenté comme non exécutable, pas simulé. Procédure : `PW_FIREFOX=1 npx playwright test` sur une autre machine. |
| Comment évitez-vous les tests fragiles ? | Sélecteurs accessibles (`getByRole`, `getByLabel`) plutôt que CSS, attentes automatiques de Playwright, session de connexion réutilisée par `auth.setup.ts`. |
| Comment mesurez-vous l'accessibilité ? | axe-core 4.10, règles WCAG 2.0/2.1 A et AA, sur 6 pages : 0 violation, toutes sévérités. Preuve : `quality/reports/a11y/*.json` (tableaux vides). |
| axe suffit-il pour l'accessibilité ? | Non, un outil automatique ne détecte qu'une partie des problèmes ; on complète par conception : libellés visibles, lien « Aller au contenu », navigation clavier, messages d'erreur clairs. |
| Et les tests Vitest ? | 403 tests de composants et pages (Testing Library), 32 fichiers : rendu, filtres, erreurs, rechargement. Preuve : `unit/frontend-junit.xml`. |

### Membre 3 — Performance, fiabilité, portabilité

Commandes : `bash quality/run-all.sh perf`, `bash quality/run-all.sh reliability`, `bash quality/run-all.sh install`.

| Question probable | Réponse |
|---|---|
| Que mesure votre test de charge ? | `quality/perf/load-test.js` : 20 utilisateurs simultanés pendant 1 min, mélange réaliste des 3 rôles (recherche, détail, candidatures, statistiques, audit), sur 3 068 offres. |
| Résultats ? | 4 463 requêtes (72 req/s), p95 = 349,6 ms (seuil 500), médiane 55 ms, 0,00 % d'erreurs. Preuve : `quality/reports/perf/k6-console.txt`. |
| Pourquoi le p95 et pas la moyenne ? | La moyenne cache les lenteurs ; le p95 garantit que 95 % des utilisateurs sont servis sous 500 ms. |
| Le max est à 1,5 s, c'est grave ? | Une requête isolée (démarrage à froid, ramasse-miettes) ; le seuil porte sur le p95, tenu avec marge. |
| Le seuil est-il vérifié automatiquement ? | Oui, il est codé dans le script (`thresholds`) : k6 sort en échec s'il n'est pas tenu. |
| Limite de votre mesure ? | Toute la stack tourne sur un seul poste : les valeurs absolues dépendent du matériel. |
| Comment testez-vous la fiabilité ? | 8 scénarios pytest qui provoquent de vraies pannes (`docker compose stop/start/restart`) : IA arrêtée, service candidatures arrêté, PostgreSQL redémarré, transition interdite, doublon. |
| Ont-ils trouvé quelque chose ? | Oui, 2 défauts (première exécution 5/8) : une offre créée pendant la panne IA n'était jamais analysée → relance automatique chaque minute (`JobAnalysisListener`) ; le gateway restait bloqué > 30 s sur un service arrêté → délai de connexion de 3 s. Résultat final 8/8. |
| Prouvez l'absence de perte de données. | Le scénario « PostgreSQL redémarré » recrée offre + candidature + statut puis les relit après redémarrage : tout est là. Preuve : `reliability/junit.xml`. |
| Comment prouvez-vous la portabilité ? | Un `Dockerfile` par composant, `docker compose up -d --build` installe tout ; la CI GitHub Actions (Ubuntu) reconstruit et teste tout (20/20 jobs), et déploie sur une VM Azure (Kubernetes) avec tests de fumée (22/22). Guide : [GUIDE_INSTALLATION.md](GUIDE_INSTALLATION.md). |

### Membre 4 — Sécurité et maintenabilité

Commandes : `bash quality/run-all.sh sast`, `sca`, `dast`, `maintainability`.

| Question probable | Réponse |
|---|---|
| Différence SAST / SCA / DAST ? | SAST : lit notre code source (Bandit, Semgrep). SCA : cherche les CVE connues dans nos dépendances (pip-audit, npm audit, Trivy sur les 8 images). DAST : attaque l'application en fonctionnement (OWASP ZAP). |
| Résultats ? | SAST 0 problème (Semgrep : 355 règles, 498 fichiers) ; SCA 179 → 0 CVE dans les images (dont 24 critiques), 82 → 0 Python, 1 → 0 npm ; ZAP 0 échec, 59 contrôles OK. Preuves : `quality/reports/security/`, versions `-avant` conservées. |
| Comment avez-vous corrigé les CVE ? | Montées de version (Spring Boot 4.1.2, jsoup 1.24.1, FastAPI/starlette, pypdf…), pip retiré de l'image IA, puis **tous les tests rejoués**. |
| Il reste 3 alertes ZAP ? | Risques acceptés et justifiés : `unsafe-inline` pour les styles React, images d'entreprise externes, pas de SRI possible sur Google Fonts (feuille générée dynamiquement). |
| Comment est fait le contrôle d'accès ? | JWT vérifié au gateway, identité transmise dans des en-têtes signés HMAC (appel direct sans signature → 401), `@PreAuthorize` par rôle dans chaque service. Testé : 401 sans jeton, 403 hors rôle (`RbacIntegrationTest` ×5, e2e API, `/forbidden`). |
| Force brute ? | Verrouillage après 5 échecs en 15 min ; 10 requêtes / 5 min / IP puis 429. Preuve d'exécution réelle : `security/rate-limit-429.txt`. |
| OWASP Top 10 ? | A01 contrôle d'accès (RBAC) ; A02 crypto (BCrypt, CV chiffrés, JWT signés) ; A03 injection (JPA paramétré, assainissement XSS jsoup) ; A05 configuration (ZAP, en-têtes CSP…) ; A06 composants vulnérables (SCA) ; A07 authentification (verrouillage) ; A09 journalisation (audit). |
| Comment mesurez-vous la maintenabilité ? | Couverture (JaCoCo, pytest-cov, Vitest), complexité lizard : CCN moyen 2,2, 5 fonctions sur 875 au-dessus de 15 ; lint ruff et oxlint à 0, bloquants en CI. Structure en couches dans chaque service. |
| Ces analyses tournent-elles automatiquement ? | Bandit, pip-audit, npm audit, lint et tests sont dans la CI ; Semgrep, Trivy et ZAP via `run-all.sh`. |

### Questions transverses (lead, ou n'importe quel membre)

| Question probable | Réponse |
|---|---|
| Le cahier dit 2 à 3 étudiants, vous êtes 4. | *(préparer la réponse validée avec l'encadrant)* ; chaque membre porte un bloc ISO distinct, la répartition est documentée. |
| Comment savoir que vos résultats ne sont pas inventés ? | Fichiers générés par les outils (junit.xml, JSON, HTML) horodatés, exécutions CI GitHub Actions liées dans la matrice, et on peut tout relancer devant vous. |
| Qu'est-ce que la matrice de traçabilité ? | Pour chaque exigence (EF, RG, EQ) : le ou les tests, l'outil, le résultat et le fichier de preuve. |
| Si vous aviez plus de temps ? | Exécuter Firefox sur une autre machine, tests de charge sur une infrastructure séparée de l'application, recette avec de vrais utilisateurs. |
