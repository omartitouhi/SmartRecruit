# Fiche d'étude — Membre 4 : sécurité et qualité de code

> Pour : *(à compléter)*. Complète le [guide de soutenance](SOUTENANCE.md) (questions-réponses, membre 4)
> et la [répartition des tests](REPARTITION_TESTS.md).

## 1. Ta mission en soutenance

Tu dois pouvoir **expliquer** les mécanismes de sécurité, **relancer** les analyses, **montrer** les rapports
avant/après et **répondre** sur :

- **6.6 Sécurité** : authentification robuste, contrôle d'accès par rôle, absence de failles connues (OWASP Top 10),
  avec les couches SAST, SCA et DAST ;
- **6.7 Maintenabilité** (analyse statique) : complexité, lint, structure en couches.

## 2. Ce que tu dois apprendre (notions)

| Notion | Ce que tu dois savoir dire |
|---|---|
| SAST | Analyse statique : lit le code source sans l'exécuter pour trouver des motifs dangereux (Bandit pour Python, Semgrep multi-langages). |
| SCA | Analyse des dépendances : compare les versions des bibliothèques aux bases de CVE (pip-audit, npm audit, Trivy sur les images Docker). |
| DAST | Analyse dynamique : attaque l'application en cours d'exécution, de l'extérieur (OWASP ZAP). |
| CVE, gravité | Identifiant public d'une vulnérabilité connue ; gravité LOW → CRITICAL (score CVSS). On a filtré HIGH et CRITICAL avec Trivy. |
| Faux positif / risque accepté | Alerte non applicable, ou risque connu et justifié par écrit (nos 3 alertes ZAP). |
| OWASP Top 10 (2021) | Les 10 catégories de failles web les plus courantes : A01 contrôle d'accès, A02 cryptographie, A03 injection, A05 mauvaise configuration, A06 composants vulnérables, A07 authentification, A09 journalisation… |
| JWT | Jeton signé contenant l'identité et le rôle, avec une date d'expiration ; le serveur vérifie la signature, il ne fait pas confiance au contenu sans elle. |
| RBAC | Contrôle d'accès par rôle (`CANDIDATE`, `RECRUITER`, `ADMIN`) ; chez nous `@PreAuthorize` sur chaque point d'API. |
| HMAC | Signature d'un message avec une clé secrète partagée : le gateway signe les en-têtes d'identité, chaque service vérifie ; un appel direct sans signature reçoit 401. |
| BCrypt | Hachage lent et salé des mots de passe : impossible de retrouver le mot de passe, coûteux à attaquer par force brute. |
| XSS et assainissement | Injection de script dans une page ; on nettoie toutes les chaînes JSON entrantes (jsoup). |
| Injection SQL | Requête construite avec des données utilisateur ; évitée par JPA/JPQL paramétré. |
| Rate limiting, verrouillage | Limiter le nombre de requêtes (429) et verrouiller un compte après plusieurs échecs : contre la force brute. |
| En-têtes de sécurité, CSP | Le navigateur applique des règles envoyées par le serveur (Content-Security-Policy, etc.) ; ZAP vérifie leur présence. |
| Complexité cyclomatique (CCN) | Nombre de chemins indépendants dans une fonction ; au-delà de 15 (notre seuil lizard), elle devient difficile à tester et à maintenir. |
| Lint | Outil qui signale le code suspect ou non conforme aux règles (ruff pour Python, oxlint pour TypeScript). |

## 3. Fichiers à lire, dans l'ordre

| # | Fichier | Ce que tu y cherches |
|---|---|---|
| 1 | `backend/api-gateway/src/main/java/com/smartrecruit/gateway/security/JwtAuthenticationGatewayFilter.java` | Vérification du JWT à l'entrée (401 si absent ou invalide). |
| 2 | `…/gateway/security/InternalIdentityHeaders.java` et `RateLimitingFilter.java` | Signature HMAC des en-têtes d'identité ; limite de 10 requêtes / 5 min / IP (429). |
| 3 | `backend/application-service/src/main/java/com/smartrecruit/application/security/InternalIdentityVerifier.java` et `config/SecurityConfig.java` | Côté service : vérification de la signature, règles par rôle. |
| 4 | `…/application/config/JacksonSanitizationConfig.java` et `security/HtmlSanitizer.java` | Assainissement XSS de toutes les chaînes JSON entrantes. |
| 5 | `backend/auth-service/src/main/java/com/smartrecruit/auth/service/AuthService.java` et `config/SecurityConfig.java` | Hachage BCrypt, verrouillage après 5 échecs en 15 min (RG-06). |
| 6 | `backend/user-service/src/main/java/com/smartrecruit/user/storage/CvCipher.java` | Chiffrement des CV stockés. |
| 7 | `backend/*/src/test/java/…/security/RbacIntegrationTest.java` et `backend/api-gateway/src/test/java/…/RateLimitingIntegrationTest.java` | Les tests 401/403 et 429. |
| 8 | `quality/run-all.sh`, étapes `sast`, `sca`, `dast`, `maintainability` | Quels outils, quelles règles (Semgrep : `p/owasp-top-ten`, `p/java`, `p/typescript`, `p/python`, `p/secrets`), quels fichiers exclus. |
| 9 | `quality/reports/security/` : `trivy-resume.txt`, `*-avant*`, `zap/` | Comparer avant/après correction. |

## 4. Commandes à savoir lancer (et lire)

```bash
bash quality/run-all.sh sast
```

Lire `quality/reports/security/bandit.txt` (aucun problème) et le résumé de `semgrep.txt`.

```bash
bash quality/run-all.sh sca
```

Images Docker construites au préalable. Lire `pip-audit.txt`, `npm-audit.txt`, puis `trivy-resume.txt` (0 HIGH/CRITICAL).

```bash
bash quality/run-all.sh dast
```

Application démarrée. Ouvrir `quality/reports/security/zap/zap-frontend.html` : FAIL, WARN, PASS.

```bash
bash quality/run-all.sh maintainability
```

Lire les dernières lignes de `quality/reports/maintainability/lizard-complexite.txt` (CCN moyen, fonctions au-dessus
du seuil), puis `ruff.txt` et `oxlint.txt`.

## 5. Chiffres à connaître par cœur

| Sujet | Valeur |
|---|---|
| SAST | **0** problème ; Semgrep : **355** règles sur **498** fichiers |
| SCA images (Trivy) | **179 → 0** CVE HIGH/CRITICAL, dont **24** critiques, sur **8** images |
| SCA Python / npm | **82 → 0** (pip-audit), **1 → 0** (npm audit) |
| DAST (ZAP) | **0** échec, **59** contrôles réussis, **3** alertes moyennes acceptées |
| Anti-force brute | verrouillage après **5** échecs en **15 min** ; **10** requêtes / **5 min** / IP sur connexion et inscription, puis **429** |
| Complexité | CCN moyen **2,2** ; **5** fonctions sur **875** au-dessus de 15 |
| Lint | ruff et oxlint : **0** avertissement, bloquants en CI |

## 6. Preuves à savoir ouvrir

- `quality/reports/security/bandit.txt`, `semgrep.txt`.
- `quality/reports/security/pip-audit-avant.txt` puis `pip-audit.txt` ; `trivy-avant/` puis `trivy/` et `trivy-resume.txt`.
- `quality/reports/security/zap/zap-frontend.html` (et `zap-avant/` pour comparer).
- `quality/reports/security/rate-limit-429.txt` : preuve d'exécution réelle de la limite.
- `quality/reports/maintainability/lizard-complexite.txt`, `ruff.txt`, `oxlint.txt`.

## 7. Questions pièges (au-delà du guide de soutenance)

| Question | Réponse |
|---|---|
| Où est stocké le JWT et que se passe-t-il s'il est volé ? | Dans le `localStorage` du navigateur (`frontend/src/api/client.ts`). Volé, il reste valable jusqu'à son expiration : **1 h** pour le jeton d'accès (7 jours pour le jeton de renouvellement, `auth-service/…/application.yml`). Le principal risque de vol est le XSS, d'où l'assainissement des entrées et la CSP. Un cookie `HttpOnly` serait une amélioration possible. |
| Pourquoi des en-têtes signés en plus du JWT ? | Le JWT est vérifié une fois au gateway. Sans signature, n'importe qui accédant au réseau interne pourrait appeler un service en se prétendant admin. La signature HMAC garantit que l'identité vient du gateway, et une signature de plus de **2 minutes** est refusée (`InternalIdentityVerifier`), ce qui empêche de rejouer une requête capturée. |
| SonarQube était proposé : pourquoi ne pas l'utiliser ? | Aucun outil n'est imposé (§7). Semgrep (sécurité), lizard (complexité), ruff/oxlint (lint) et JaCoCo/pytest-cov/Vitest (couverture) couvrent les mêmes rôles, s'exécutent en local et en CI sans serveur à héberger. |
| Comment avez-vous corrigé 179 CVE ? | Montées de version des dépendances et des images de base (Spring Boot 4.1.2, jsoup 1.24.1, FastAPI/starlette, pypdf…), pip retiré de l'image IA, puis tous les tests rejoués pour vérifier qu'aucune régression n'apparaissait. |
| 0 vulnérabilité, cela veut dire que l'application est sûre ? | Non : 0 vulnérabilité **connue détectée** par ces outils. Ils ne trouvent pas les failles de logique métier ; c'est pourquoi les règles d'accès sont aussi testées (401/403, RBAC, `/forbidden`). |
| Pourquoi accepter 3 alertes ZAP ? | Elles sont justifiées : `unsafe-inline` nécessaire pour les styles React, images d'entreprise hébergées à l'extérieur, pas de SRI possible sur Google Fonts (feuille générée dynamiquement). Accepter un risque documenté est une décision, pas un oubli. |
| Les 5 fonctions complexes, un problème ? | 5 sur 875, au-dessus du seuil de 15 : signalées et toutes testées ; candidates à un découpage si elles évoluent. |
| La sécurité des CV ? | Fichiers chiffrés au stockage (`CvCipher`), consultation journalisée dans l'audit (`CV_VIEWED`), effacement RGPD possible. |

## 8. Plan d'apprentissage

| Étape | Durée | Objectif |
|---|---|---|
| 1 | 2 h | Apprendre les notions de la section 2, en priorité l'OWASP Top 10 2021 (page officielle) et le fonctionnement d'un JWT. |
| 2 | 2 h | Lire les fichiers 1 à 7 de la section 3 ; suivre une requête du navigateur au service (figure 7 du rapport de conception). |
| 3 | 1 h | Lancer `sast`, `sca`, `maintainability` ; ouvrir chaque preuve de la section 6. |
| 4 | 1 h | Lancer `dast` (application démarrée) et parcourir le rapport ZAP ; comparer avec `zap-avant/`. |
| 5 | 1 h | Réciter les réponses du guide de soutenance (membre 4) et de la section 7. |

## 9. Auto-évaluation

- [ ] J'explique SAST, SCA et DAST avec l'outil que nous utilisons pour chacun.
- [ ] Je suis le chemin d'une requête : JWT au gateway → en-têtes HMAC → vérification dans le service → `@PreAuthorize`.
- [ ] Je relie au moins 6 catégories OWASP Top 10 à un mécanisme et à un test du projet.
- [ ] Je montre un rapport avant/après (Trivy ou pip-audit) et j'explique la correction.
- [ ] Je justifie les 3 alertes ZAP acceptées.
- [ ] J'explique la complexité cyclomatique et je lis la fin du rapport lizard.
