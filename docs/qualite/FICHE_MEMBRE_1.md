# Fiche d'étude — Membre 1 (lead) : présentation, logique métier et API

> Pour : *(à compléter)* — le membre qui connaît le mieux le projet.
> Complète le [guide de soutenance](SOUTENANCE.md) (questions-réponses) et la [répartition des tests](REPARTITION_TESTS.md).

## 0. Pourquoi cette partie revient au membre qui connaît le mieux le projet

- **Le lead présente tout** : sujet, architecture, choix techniques, démo, synthèse ISO 25010. Il faut une vue
  d'ensemble que les autres n'ont pas.
- **La logique métier est la partie la plus difficile à apprendre vite** : 6 services, 9 règles métier, un workflow,
  1 233 tests. Les autres blocs reposent surtout sur un outil et ses rapports (k6, Playwright, ZAP…), qui s'apprennent
  en quelques jours ; la logique métier demande de comprendre le code.
- **C'est ce que l'enseignant vérifie en premier** (§4 et §6.1 : « toutes les fonctionnalités implémentées et
  exactes »). Si une question dérape sur le code, c'est toi qui dois pouvoir répondre.
- **Tu es le filet de sécurité** : quand un membre bloque, tu reprends (« en complément… »). Tu dois donc connaître
  une phrase-clé de chaque autre partie (section 7).

## 1. Ta mission en soutenance

1. Présenter le projet en ≈ 12 min (déroulé : [guide de soutenance §2](SOUTENANCE.md)).
2. Faire la démo live de l'application et lancer les **2 couches de test en direct** (Playwright et k6).
3. Répondre sur ton bloc : **6.1 Aptitude fonctionnelle** et **6.7 Maintenabilité (couverture)** ; couches
   unitaires, intégration, API.
4. Répondre aux questions transverses (traçabilité, règle d'or, taille du groupe).

## 2. Ce que tu dois apprendre (notions)

| Notion | Ce que tu dois savoir dire |
|---|---|
| ISO/IEC 25010 | Modèle de qualité du produit logiciel, **8 caractéristiques** : aptitude fonctionnelle, efficacité de performance, compatibilité, facilité d'utilisation, fiabilité, sécurité, maintenabilité, portabilité. Pour chacune : une exigence mesurable, un test, un outil, un résultat. |
| Traçabilité | Relier chaque exigence (EF, RG, EQ) à ses tests, son outil, son résultat et sa preuve : c'est la [matrice](MATRICE_TRACABILITE.md). |
| Pyramide des tests | Beaucoup de tests unitaires (rapides, ciblés), moins d'intégration, peu d'E2E (lents, coûteux). Chez nous : 1 233 unitaires/intégration, 155 API, 62 E2E. |
| Test unitaire | Une classe isolée (règle de scoring, validation), sans base ni réseau. |
| Test d'intégration | Plusieurs couches ensemble : requête HTTP simulée (MockMvc) → contrôleur → service → repository → base H2. Les autres microservices sont remplacés par de **faux serveurs HTTP**. |
| Test d'API (contrat) | Un vrai client HTTP contre la stack Docker complète : codes HTTP et structure JSON. |
| Double de test (mock, stub, faux serveur) | Remplace une dépendance pour isoler ce qu'on teste et rendre le test reproductible. |
| Test paramétré | Un même test exécuté sur une liste de cas (ex. toutes les paires de statuts pour RG-03). |
| Codes HTTP | 400 requête invalide / transition interdite ; 401 non authentifié ; 403 authentifié mais rôle insuffisant ; 404 introuvable ; 409 conflit avec l'état (doublon, offre non publiée) ; 422 données valides mais non traitables (profil incomplet) ; 429 trop de requêtes. |
| Machine à états (workflow) | Des statuts et une table de transitions autorisées ; tout le reste est refusé. |
| Couverture de code | % de lignes (ou branches) exécutées par les tests ; mesure ce qui est **exécuté**, pas ce qui est **vérifié**. |
| Architecture en couches | `controller` (HTTP) → `service` (règles) → `repository` (accès base) → `model` (entités), plus `dto`, `exception`, `client`. |
| Microservices | Un service par domaine, une base par service, communication REST ; le gateway est la seule porte d'entrée. |

## 3. Le projet vu de ta partie : fichiers à lire, dans l'ordre

| # | Fichier | Ce que tu y cherches |
|---|---|---|
| 1 | Le cahier des charges (PDF de l'enseignant) puis [RAPPORT_CONCEPTION.md](RAPPORT_CONCEPTION.md) | Le §4 du cahier et comment chaque exigence est réalisée ; les 8 figures. |
| 2 | [MATRICE_TRACABILITE.md](MATRICE_TRACABILITE.md) | Chaque ligne EF/RG/EQ : savoir la lire à voix haute. |
| 3 | `backend/application-service/src/main/java/com/smartrecruit/application/model/ApplicationStatus.java` | La table `ALLOWED_TRANSITIONS` (RG-03). |
| 4 | `…/application/service/ApplicationService.java` | Postuler (RG-04, RG-05), changer de statut, historique, audit. |
| 5 | `…/application/controller/ApplicationController.java` et `exception/GlobalExceptionHandler.java` | Comment une erreur métier devient un code HTTP (400/409/422). |
| 6 | `…/application/client/` (`JobDirectoryClient`, `CandidateReadinessClient`, `AiMatchingClient`, `AuditClient`) | Les appels inter-services ; l'IA en « best effort ». |
| 7 | `backend/application-service/src/test/java/…/controller/StatusChangeIntegrationTest.java` et `ApplyIntegrationTest.java` | Structure d'un test d'intégration : préparation, appel MockMvc, assertions. |
| 8 | `backend/auth-service/src/test/java/…/controller/LoginIntegrationTest.java` | RG-06 (verrouillage après 5 échecs). |
| 9 | `ai-service/app/services/scoring.py`, `matching_service.py`, `rules_match.py` + `ai-service/tests/test_scoring.py` | Comment le score de compatibilité est calculé et testé. |
| 10 | `backend/e2e/e2e_full.py` | Les 155 vérifications API, en passant par le gateway et nginx. |
| 11 | `quality/run-all.sh` | Les 10 étapes de la campagne (install, unit, api, ui, perf, reliability, sast, sca, dast, maintainability). |

## 4. Commandes à savoir lancer (et lire)

```bash
bash quality/run-all.sh unit
```

Tests unitaires + intégration des 6 services Java, du service IA et du frontend, avec couverture. Lire : le nombre de
tests et `BUILD SUCCESS` / `passed` ; rapports dans `quality/reports/unit/` et `quality/reports/coverage/`.

```bash
cd ai-service && pytest -q
```

Version rapide (quelques secondes, 137 tests) : ton **plan B** si une démo échoue.

```bash
bash quality/run-all.sh api
```

Application démarrée. Lire la dernière ligne de `quality/reports/api/e2e-console.txt` : 155 vérifications OK.

Démo de la soutenance (tu les lances, les membres 2 et 3 les expliquent si on leur demande) :

```bash
cd quality/e2e-ui && npx playwright test --project=chromium --headed
```

```bash
docker run --rm -v "$(pwd -W 2>/dev/null || pwd)/quality/perf:/scripts:ro" -v "$(pwd -W 2>/dev/null || pwd)/quality/reports/perf:/reports" grafana/k6:1.0.0 run /scripts/load-test.js
```

## 5. Chiffres à connaître par cœur

| Sujet | Valeur |
|---|---|
| Tests unitaires + intégration | **1 233** = 693 JUnit (6 services) + 137 pytest (IA) + 403 Vitest (front) |
| Tests API | **155/155** |
| Couverture des lignes | Java **95,7 %** (91,4 à 97,7 % selon le service), IA **97,2 %**, frontend **92,1 %** |
| Règles métier | **9** (RG-01 à RG-09), au moins un test nommé par règle |
| Volume pour la recherche | **3 068** offres publiées |
| Campagne finale | 11/11/2026, toutes les étapes réussies en un passage (`quality/reports/campagne-finale-console.txt`) |
| Les autres blocs (pour la synthèse) | E2E 62/62 sur 4 navigateurs ; p95 349,6 ms < 500 ms ; fiabilité 8/8 après 2 défauts corrigés ; CVE 179 → 0 ; ZAP 0 échec ; CI 20/20 jobs |

## 6. Preuves à savoir ouvrir

- `quality/reports/coverage/job-service/index.html` (rapport JaCoCo : montrer une classe verte/rouge).
- `quality/reports/unit/<service>/` (résultats JUnit) et `quality/reports/unit/ai-service-junit.xml`.
- `quality/reports/api/e2e-console.txt`.
- [MATRICE_TRACABILITE.md](MATRICE_TRACABILITE.md) et [RAPPORT_CONFORMITE_ISO25010.md](RAPPORT_CONFORMITE_ISO25010.md) : savoir
  aller directement à une ligne demandée.

## 7. Le minimum à savoir sur les autres parties (filet de sécurité)

| Partie | Une phrase |
|---|---|
| Membre 2 — Interface | Playwright rejoue 13 parcours réels sur Chromium, Edge, WebKit et Chrome mobile ; axe-core : 0 violation WCAG 2.1 A/AA sur 6 pages ; Firefox documenté non exécutable (règle d'or). |
| Membre 3 — Performance, fiabilité, portabilité | k6 : 20 utilisateurs pendant 1 min, p95 349,6 ms ; 8 pannes réelles de conteneurs, 2 défauts trouvés et corrigés ; tout s'installe avec `docker compose up -d --build`, CI 20/20 jobs. |
| Membre 4 — Sécurité, qualité de code | SAST 0 (Bandit, Semgrep), SCA 179 → 0 CVE (Trivy), ZAP 0 échec ; JWT + RBAC + en-têtes signés HMAC ; CCN moyen 2,2, lint à 0. |

## 8. Questions pièges (au-delà du guide de soutenance)

| Question | Réponse |
|---|---|
| Pourquoi des microservices pour un projet étudiant ? | Un service par domaine (comptes, profils, offres, candidatures, statistiques, IA) : responsabilités séparées et testables isolément, et une panne d'un service n'arrête pas les autres (prouvé par les tests de fiabilité). Le prix : de la complexité, compensée par Docker Compose. |
| Comment garantir la cohérence sans clés étrangères entre bases ? | Clés étrangères logiques + vérifications par appel inter-service avant d'écrire (ex. l'offre existe et est `PUBLISHED` avant d'accepter une candidature). |
| Différence entre 409 et 422 dans votre API ? | 409 : conflit avec l'état actuel (doublon, offre non publiée). 422 : la requête est correcte mais le candidat ne remplit pas une condition (profil incomplet, pas de CV). |
| Un test qui passe toujours, comment savoir qu'il teste quelque chose ? | Chaque règle a une assertion explicite sur le résultat **et** sur l'état (ex. RG-03 : 400 **et** statut inchangé). On a vu des tests échouer avant correction (fiabilité 5/8 au premier passage). |
| Où est l'exigence « CRUD complet sur 4 entités » ? | Rapport de conception §3 : 6 entités avec les 4 opérations ; tests `JobDeleteIntegrationTest`, `NotesIntegrationTest`… (matrice EF-02). |
| Que se passe-t-il si l'IA est en panne pendant une candidature ? | La candidature est enregistrée (201), le score reste `null` : l'IA est appelée en « best effort » et de façon asynchrone (figure 7 du rapport de conception). |
| Votre IA est-elle testée alors qu'elle dépend d'un LLM externe ? | Le scoring par règles est déterministe et testé (pytest, 97,2 % de couverture) ; l'appel au LLM externe est testé avec un client simulé (`test_llm_client.py`). |

## 9. Plan d'apprentissage

| Étape | Durée | Objectif |
|---|---|---|
| 1 | 2 h | Relire le cahier des charges, le rapport de conception et la matrice : savoir raconter le projet sans notes. |
| 2 | 3 h | Lire les fichiers 3 à 8 de la section 3 ; refaire à voix haute le parcours « postuler » (figure 7). |
| 3 | 1 h | Lancer `unit` et `api`, ouvrir chaque preuve de la section 6. |
| 4 | 2 h | Répéter la présentation de 12 min avec la démo et les 2 commandes live, chronomètre en main. |
| 5 | 1 h | Faire interroger chaque membre par un autre avec le guide de soutenance ; noter ce qui bloque. |

## 10. Auto-évaluation (tout doit être coché la veille)

- [ ] Je cite les 8 caractéristiques ISO 25010 et une preuve pour chacune.
- [ ] Je dessine l'architecture de mémoire (gateway, 6 services Spring, IA FastAPI, une base par service).
- [ ] J'explique RG-03 et RG-04 en montrant le code **et** le test.
- [ ] Je justifie 400 / 401 / 403 / 409 / 422 / 429 avec un exemple de notre API.
- [ ] Je lance `unit`, `api`, Playwright et k6 sans aide, et je sais quoi lire dans leur sortie.
- [ ] Je retrouve en moins de 30 s n'importe quelle ligne de la matrice et sa preuve.
- [ ] Je tiens la présentation en 12 min.
