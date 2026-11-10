# Soutenance (10 minutes) — déroulé et commandes

Objectif imposé : démonstration live de l'application **et d'au moins deux couches de test en exécution**.

## Avant la séance (J-1 et 15 min avant)

```bash
docker compose up -d --build
```

```bash
python backend/seed/seed_demo.py
```

- Vérifier http://localhost:5173 (connexion `candidat@smartrecruit.test` / `Motdepasse123`).
- Dans `quality/e2e-ui`, `npm ci` et `npx playwright install chromium webkit` déjà faits ; image `grafana/k6:1.0.0`
  déjà téléchargée (`docker pull grafana/k6:1.0.0`).
- **Ne pas se connecter plus de 10 fois en 5 minutes** avant la démo (limitation anti-force brute) : les tests
  Playwright se connectent 4 fois.
- Ouvrir à l'avance dans le navigateur : `quality/reports/e2e-ui/html/index.html`,
  `quality/reports/coverage/job-service/index.html`, `docs/qualite/RAPPORT_CONFORMITE_ISO25010.md`.

## Déroulé

| Temps | Contenu | Qui |
|---|---|---|
| 0:00 – 1:00 | Domaine (recrutement), architecture (6 microservices + IA + React), stack et justification | membre 1 |
| 1:00 – 4:00 | **Démo application** : candidat (recherche filtrée → offre → score expliqué → candidature) ; recruteur (tableau de bord, classement, changement de statut) ; admin (journal d'audit filtré, désactivation d'un compte) | membre 2 |
| 4:00 – 5:30 | **Couche de test live n°1 — E2E Playwright** (commande ci-dessous, navigateur visible) | membre 3 |
| 5:30 – 7:00 | **Couche de test live n°2 — charge k6** (20 utilisateurs, 1 min ; commenter p95 et seuils pendant l'exécution) | membre 1 |
| 7:00 – 9:00 | Matrice de traçabilité et rapport ISO 25010 : une preuve par caractéristique ; les **défauts trouvés par nos tests et corrigés** (fiabilité, CVE) | membre 2 |
| 9:00 – 10:00 | Questions | tous |

### Couche live n°1 — tests E2E de l'interface

```bash
cd quality/e2e-ui && npx playwright test --project=chromium --headed
```

Environ 40 s : connexion réelle des 3 rôles, parcours candidat / recruteur / admin, refus d'accès (`/forbidden`),
audit d'accessibilité axe. Montrer ensuite le rapport HTML (`npx playwright show-report ../reports/e2e-ui/html`).

### Couche live n°2 — test de charge

```bash
docker run --rm -v "$(pwd -W 2>/dev/null || pwd)/quality/perf:/scripts:ro" -v "$(pwd -W 2>/dev/null || pwd)/quality/reports/perf:/reports" grafana/k6:1.0.0 run /scripts/load-test.js
```

(à lancer depuis la racine du dépôt, dans Git Bash ; 1 min). Lire à voix haute : nombre de requêtes, p95, taux
d'erreur, seuils `OK`.

### Plan B (si une démo live échoue)

- Couche alternative rapide : `cd ai-service && pytest -q` (137 tests en 3 s) ou `cd frontend && npm test` (398 tests).
- Montrer les rapports déjà générés dans `quality/reports/` (ils viennent d'exécutions réelles, horodatées).

## Questions probables et réponses courtes

- **« Comment prouvez-vous la fiabilité ? »** On arrête réellement des conteneurs pendant les tests ; ces tests ont
  trouvé deux défauts (analyse IA perdue pendant une panne, gateway bloqué > 30 s) que nous avons corrigés, puis 8/8.
- **« Pourquoi pas Firefox ? »** Le binaire Playwright est bloqué par Windows sur notre poste : documenté comme non
  exécutable (règle d'or), compatibilité prouvée sur 4 navigateurs dont WebKit (Safari), procédure fournie.
- **« La couverture ? »** JaCoCo 91 à 98 % des lignes selon le service, IA 97 %, frontend 93 %.
- **« OWASP Top 10 ? »** A01 contrôle d'accès (RBAC, tests 403), A03 injection (JPA paramétré, assainissement XSS,
  Semgrep), A06 composants vulnérables (pip-audit, npm audit, Trivy : CVE corrigées), A07 authentification
  (verrouillage, rate limiting), A05 configuration (ZAP, en-têtes de sécurité).
