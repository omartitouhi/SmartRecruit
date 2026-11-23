# Fiche d'étude — Membre 3 : performance, fiabilité, portabilité

> Pour : *(à compléter)*. Complète le [guide de soutenance](SOUTENANCE.md) (questions-réponses, membre 3)
> et la [répartition des tests](REPARTITION_TESTS.md).

## 1. Ta mission en soutenance

Le lead lance k6 en direct ; toi, tu dois pouvoir **lire le résultat à voix haute**, **relancer** tes tests et
**répondre** sur :

- **6.2 Efficacité de performance** (temps de réponse sous charge) ;
- **6.5 Fiabilité** (comportement en cas de panne, pas de perte de données) ;
- **6.8 Portabilité** (installation reproductible, CI/CD) ;
- la compatibilité avec un système tiers (API LLM externe).

## 2. Ce que tu dois apprendre (notions)

| Notion | Ce que tu dois savoir dire |
|---|---|
| Test de charge / stress / endurance | Charge : comportement sous un trafic attendu (notre cas, 20 utilisateurs). Stress : on augmente jusqu'à la rupture. Endurance : charge longue durée (fuites mémoire). |
| Utilisateur virtuel (VU) | Un script qui rejoue un utilisateur en boucle ; k6 en fait tourner 20 en parallèle. |
| Débit | Requêtes par seconde (req/s). |
| Latence, médiane, percentile | p95 = 95 % des requêtes sont plus rapides que cette valeur. La moyenne cache les requêtes lentes ; le p95 les fait apparaître. |
| Seuil (threshold) | Critère d'acceptation codé dans le script (`p(95)<500`) : k6 échoue s'il n'est pas tenu, donc le verdict est automatique. |
| Fiabilité, tolérance aux pannes | Le système continue de rendre service quand un composant tombe, et revient à l'état normal ensuite. |
| « Best effort » | Un appel non indispensable (l'IA) peut échouer sans bloquer l'action principale (la candidature reste enregistrée, score `null`). |
| Délai d'attente (timeout) | Ne pas attendre indéfiniment un service arrêté : le gateway abandonne après 3 s et répond une erreur claire. |
| Reprise automatique (retry) | Réessayer plus tard un traitement échoué (relance de l'analyse IA des offres chaque minute). |
| Test de chaos ciblé | Provoquer volontairement une vraie panne (arrêt d'un conteneur) et vérifier le comportement. |
| Durabilité des données | Une transaction validée survit au redémarrage de la base (PostgreSQL + volume Docker). |
| Conteneur, image, Dockerfile, Compose | Image = application + dépendances figées ; conteneur = image en exécution ; Compose démarre tous les conteneurs ensemble. |
| CI/CD | À chaque push, GitHub Actions reconstruit et teste tout (CI), puis déploie (CD) sur la branche `main`. |
| Kubernetes, kind, Helm, Kustomize, Terraform, Ansible | Kubernetes orchestre les conteneurs (kind = Kubernetes dans Docker) ; Helm et Kustomize décrivent le déploiement ; Terraform crée l'infrastructure ; Ansible configure la VM. |
| Test de fumée (smoke test) | Vérification rapide après déploiement : les pages et l'API principales répondent. |

## 3. Fichiers à lire, dans l'ordre

| # | Fichier | Ce que tu y cherches |
|---|---|---|
| 1 | `quality/perf/load-test.js` | Le scénario `constant-vus` (20 VU, 1 min), le mélange des 3 rôles, le seuil `p(95)<500`, le résumé imprimé. |
| 2 | `quality/reports/perf/k6-console.txt` | La sortie réelle : requêtes, débit, médiane, p95, max, taux d'erreur, seuils `OK`. |
| 3 | `quality/reliability/test_reliability.py` | Les **8 scénarios** (noms en français, lisibles) et comment ils arrêtent/redémarrent les conteneurs. |
| 4 | `backend/job-service/src/main/java/com/smartrecruit/job/service/JobAnalysisListener.java` | Correctif du défaut n°1 : relance automatique des analyses IA échouées. |
| 5 | `backend/api-gateway/src/main/java/com/smartrecruit/gateway/config/GatewayConfig.java` | Correctif du défaut n°2 : délai de connexion borné à 3 s. |
| 6 | `backend/application-service/src/main/java/com/smartrecruit/application/client/AiMatchingClient.java` | L'appel à l'IA en « best effort ». |
| 7 | `docker-compose.yml` et le [guide d'installation](GUIDE_INSTALLATION.md) | Les services, les ports, les volumes, l'installation par un tiers. |
| 8 | `.github/workflows/pipeline.yml` | Les jobs : `backend`, `frontend`, `ai-service`, `kustomize`, `helm`, `terraform`, `ansible`, `images`, `deploy`, `smoke-test`. |
| 9 | `deploy/` (`k8s/`, `helm/`, `terraform/`, `ansible/`) et `docs/DEVOPS.md` | Survol : savoir dire à quoi sert chaque dossier. |
| 10 | `ai-service/tests/test_llm_client.py` | Coexistence avec un système tiers (API OpenRouter) sans dépendre du réseau. |

## 4. Commandes à savoir lancer (et lire)

Application démarrée au préalable (`docker compose up -d --build`).

```bash
bash quality/run-all.sh perf
```

Charge k6 (1 min). Lire : nombre de requêtes, req/s, p95, `http_req_failed`, puis `seuil … : OK`.

```bash
bash quality/run-all.sh reliability
```

Les 8 pannes réelles (plusieurs minutes : les conteneurs s'arrêtent et redémarrent). Lire : `8 passed`.

```bash
bash quality/run-all.sh install
```

Reconstruit et démarre toute la stack depuis le dépôt : la preuve de portabilité sur le poste.

## 5. Chiffres à connaître par cœur

| Sujet | Valeur |
|---|---|
| Charge | **20** utilisateurs simultanés pendant **1 min**, sur **3 068** offres |
| Résultat | **4 463** requêtes (**72 req/s**), p95 **349,6 ms** (seuil 500), médiane **55 ms**, max ≈ 1,5 s, **0,00 %** d'erreurs |
| Fiabilité | **8** scénarios ; premier passage **5/8**, **2 défauts** trouvés et corrigés, final **8/8** |
| CI | **20/20** jobs sur la version finale (commit `ed0779b`) |
| Déploiement | VM Azure + tests de fumée : **22/22** jobs (commit `3b4cd95`) |
| Images Docker | **8** (6 services Java, service IA, frontend) |

D'où viennent 20 et 22 jobs : 6 services backend + frontend + IA + kustomize + helm + terraform + ansible + 8 images
= 20 ; avec `deploy` et `smoke-test` = 22.

## 6. Preuves à savoir ouvrir

- `quality/reports/perf/k6-console.txt` et `k6-summary.json`.
- `quality/reports/reliability/junit.xml` et `pytest-console.txt`.
- Les exécutions GitHub Actions liées dans la [matrice](MATRICE_TRACABILITE.md) (ligne EQ-08) et le
  [rapport de conformité](RAPPORT_CONFORMITE_ISO25010.md).

## 7. Questions pièges (au-delà du guide de soutenance)

| Question | Réponse |
|---|---|
| Pourquoi 20 utilisateurs et 500 ms ? | C'est l'exemple chiffré du cahier des charges (§6.2) ; on l'a pris comme exigence mesurable et codé comme seuil. |
| Votre test de charge est-il réaliste ? | Il mélange les 3 rôles et les actions courantes (recherche, détail, candidatures, statistiques, audit) sur un fort volume. Limite : générateur de charge et application sur la même machine. |
| Comment le test de charge passe-t-il l'anti-force brute (10 connexions / 5 min / IP) ? | Le script ne se connecte que **3 fois**, dans `setup()` (un compte par rôle) ; les 20 utilisateurs virtuels réutilisent ces jetons. On mesure donc la charge réelle de l'application, pas celle de la connexion. |
| Qu'est-ce qui prouve « pas de perte de données » ? | Le scénario PostgreSQL : création d'une offre, d'une candidature et d'un statut, redémarrage de la base, relecture : tout est identique. |
| Et si le service candidatures tombe pendant qu'un utilisateur postule ? | Le gateway répond vite une erreur (pas d'attente de 30 s) ; après redémarrage, le service reprend sans perte (scénario 7). |
| Les 2 défauts trouvés auraient-ils été vus en production ? | Oui : une offre créée pendant une panne de l'IA n'était jamais analysée, et un service arrêté bloquait les utilisateurs plus de 30 s. Les tests les ont révélés avant. |
| La portabilité est-elle prouvée sur une autre machine ? | Oui par la CI : un runner Ubuntu neuf reconstruit tout à chaque push, et le déploiement part de zéro sur une VM Azure. Idéalement, faire aussi installer un membre sur son PC en suivant le guide. |
| Docker ou machine virtuelle ? | Un conteneur partage le noyau de l'hôte : plus léger et plus rapide qu'une VM. On utilise les deux : conteneurs dans une VM Azure. |

## 8. Plan d'apprentissage

| Étape | Durée | Objectif |
|---|---|---|
| 1 | 1 h | Apprendre les notions de la section 2 (vidéo ou doc k6 « load testing », bases Docker). |
| 2 | 1 h | Lire `load-test.js` et `k6-console.txt` ; savoir commenter chaque ligne du résumé. |
| 3 | 2 h | Lire `test_reliability.py` scénario par scénario ; lire les 2 correctifs (fichiers 4 et 5). |
| 4 | 1 h | Lancer `perf` puis `reliability` sur ton poste. |
| 5 | 2 h | Lire `docker-compose.yml`, `pipeline.yml`, le guide d'installation ; installer le projet sur ton PC en le suivant. |
| 6 | 1 h | Réciter les réponses du guide de soutenance (membre 3) et de la section 7. |

## 9. Auto-évaluation

- [ ] J'explique le p95 et pourquoi on ne regarde pas la moyenne.
- [ ] Je lis à voix haute un résumé k6 et je dis si le seuil est tenu.
- [ ] Je cite les 8 scénarios de fiabilité et les 2 défauts corrigés (cause et correctif).
- [ ] J'explique ce qui se passe quand l'IA est en panne pendant une candidature.
- [ ] J'explique les étapes de la CI/CD et d'où viennent 20 et 22 jobs.
- [ ] J'ai installé le projet sur un autre poste avec le guide.
