# Fiche d'étude — Membre 2 : interface utilisateur, E2E, compatibilité, accessibilité

> Pour : *(à compléter)*. Complète le [guide de soutenance](SOUTENANCE.md) (questions-réponses, membre 2)
> et la [répartition des tests](REPARTITION_TESTS.md).

## 1. Ta mission en soutenance

Le lead lance Playwright en direct ; toi, tu dois pouvoir **expliquer ce qui s'exécute**, le **relancer** si
l'enseignant le demande et **répondre** sur :

- **6.1 Aptitude fonctionnelle** (côté parcours utilisateur, tests E2E) ;
- **6.3 Compatibilité** (≥ 2 navigateurs) ;
- **6.4 Facilité d'utilisation** (parcours validés, accessibilité de base) ;
- les tests unitaires du frontend (Vitest).

## 2. Ce que tu dois apprendre (notions)

| Notion | Ce que tu dois savoir dire |
|---|---|
| Test E2E (de bout en bout) | Un vrai navigateur pilote la vraie application (frontend + gateway + services + bases), comme un utilisateur. Lent mais le plus proche du réel. |
| Test de composant (Vitest + Testing Library) | Un composant React rendu seul, l'API simulée ; rapide, vérifie l'affichage et les réactions (filtres, erreurs). |
| Playwright | Outil E2E : un fichier de config, des **projets** (un par navigateur), des **tests** dans `tests/`, un rapport HTML. |
| Projet `setup` et dépendances | `auth.setup.ts` se connecte une fois par rôle et enregistre la session ; les autres projets en dépendent et la réutilisent. |
| Auto-attente | Playwright attend qu'un élément soit visible/actionnable avant d'agir : pas de `sleep`, moins de tests instables. |
| Localisateurs accessibles | `getByRole`, `getByLabel` : on cible ce que voit l'utilisateur (rôle, libellé), pas une classe CSS fragile. |
| Test instable (« flaky ») | Un test qui passe ou échoue sans changement de code (attentes trop courtes, données partagées). |
| Moteurs de navigateur | Chromium (Chrome, Edge), WebKit (Safari), Gecko (Firefox). Tester deux moteurs différents a plus de valeur que deux navigateurs Chromium. |
| Émulation mobile | Playwright simule l'écran, le tactile et l'agent utilisateur d'un Pixel 7 ; ce n'est pas un vrai téléphone. |
| Accessibilité, WCAG 2.1 A/AA | Règles pour que l'application soit utilisable par tous (contrastes, libellés, clavier, structure). A = minimum, AA = niveau visé habituellement. |
| axe-core | Moteur qui vérifie automatiquement une partie des règles WCAG sur une page ; il ne remplace pas un test manuel. |
| Contrôle d'accès côté interface | `RequireRole` cache les pages selon le rôle ; la vraie sécurité est côté serveur (un appel API sans le bon rôle reçoit 403). |

## 3. Fichiers à lire, dans l'ordre

| # | Fichier | Ce que tu y cherches |
|---|---|---|
| 1 | `quality/e2e-ui/playwright.config.ts` | Les projets : `setup`, `chromium`, `edge`, `webkit`, `mobile-chrome`, et `firefox` seulement si `PW_FIREFOX` est défini. |
| 2 | `quality/e2e-ui/tests/accounts.ts` et `auth.setup.ts` | Les comptes de démo et la connexion préalable des rôles. |
| 3 | `quality/e2e-ui/tests/journeys.spec.ts` | Les **13 parcours** : savoir les citer et expliquer ce que vérifie chacun. |
| 4 | `quality/e2e-ui/tests/a11y.spec.ts` | Les **6 pages** auditées par axe-core et les règles appliquées. |
| 5 | `frontend/src/App.tsx` et `frontend/src/auth/RequireRole.tsx` | Les routes et la protection par rôle (redirection, page `/forbidden`). |
| 6 | `frontend/src/api/client.ts`, `frontend/src/api/mock/` | Comment l'interface appelle l'API, et la simulation utilisée par les tests Vitest. |
| 7 | Deux tests Vitest, par ex. `frontend/src/auth/RequireRole.test.tsx` et un test de page dans `frontend/src/pages/` | Structure d'un test de composant : rendu, action, assertion. |
| 8 | [MATRICE_TRACABILITE.md](MATRICE_TRACABILITE.md), lignes EF-01 à EF-06, EQ-01, EQ-03, EQ-04 | Où tes tests apparaissent comme preuve. |

## 4. Commandes à savoir lancer (et lire)

Application démarrée au préalable (`docker compose up -d --build`, données de démo chargées).

```bash
bash quality/run-all.sh ui
```

Toute la suite (4 navigateurs + accessibilité). Lire : `62 passed` (et des tests `skipped`, voir section 7).

```bash
cd quality/e2e-ui && npx playwright test --project=chromium --headed
```

Un seul navigateur, fenêtre visible : la version de la démo.

```bash
cd quality/e2e-ui && npx playwright show-report ../reports/e2e-ui/html
```

Ouvre le rapport HTML : savoir montrer un test, ses étapes et sa durée.

```bash
cd frontend && npm test
```

Tests Vitest du frontend (403 tests).

## 5. Chiffres à connaître par cœur

| Sujet | Valeur |
|---|---|
| Parcours E2E | **13**, sur **4** navigateurs/appareils (Chromium, Edge, WebKit, Chrome mobile Pixel 7) |
| Total Playwright | **62 réussis** = 13 × 4 + 6 audits d'accessibilité (Chromium) + 4 connexions de préparation |
| Accessibilité | axe-core 4.10, WCAG 2.0/2.1 A et AA, **6 pages**, **0 violation** |
| Vitest | **403** tests, 32 fichiers, couverture frontend **92,1 %** des lignes |
| Firefox | **non exécutable** sur le poste (binaire bloqué par Windows, `spawn UNKNOWN`) : documenté, pas simulé |

## 6. Preuves à savoir ouvrir

- `quality/reports/e2e-ui/html/index.html` (rapport Playwright) et `quality/reports/e2e-ui/junit.xml`.
- `quality/reports/a11y/*.json` : un fichier par page, tableau de violations vide.
- `quality/reports/unit/frontend-junit.xml` et `quality/reports/coverage/frontend/`.

## 7. Questions pièges (au-delà du guide de soutenance)

| Question | Réponse |
|---|---|
| Pourquoi des tests « skipped » dans le rapport ? | Les audits d'accessibilité ne tournent que sur Chromium : axe analyse le DOM, qui est le même quel que soit le navigateur. Les répéter n'apporterait rien. |
| Edge et Chrome, c'est le même moteur : est-ce vraiment 2 navigateurs ? | Oui, deux navigateurs, mais le même moteur. C'est pour ça qu'on teste aussi **WebKit** (moteur de Safari), un moteur réellement différent. |
| Le mobile est émulé : est-ce un vrai test de compatibilité ? | C'est une émulation (taille d'écran, tactile, agent utilisateur) : elle valide la mise en page et les parcours, pas les particularités d'un vrai appareil. Limite assumée. |
| Que se passe-t-il si un test E2E échoue ? | Playwright garde une capture d'écran (toujours) et une trace complète (en cas d'échec) dans `quality/reports/e2e-ui/artifacts` : la trace se rejoue pas à pas (DOM, réseau, console) pour trouver la cause. |
| Pourquoi ne pas tout tester en E2E ? | Trop lent et fragile ; les détails (filtres, messages d'erreur, cas limites) sont testés par Vitest, l'E2E vérifie que les parcours complets marchent ensemble. |
| Comment l'E2E respecte-t-il l'anti-force brute (RG-06) ? | La connexion est faite une seule fois par rôle (`auth.setup.ts`) puis réutilisée : 4 connexions par exécution, sous la limite de 10 par 5 minutes. |
| La facilité d'utilisation, c'est seulement axe ? | Non : parcours utilisateur validés de bout en bout + choix de conception (libellés visibles, lien « Aller au contenu », navigation clavier, messages d'erreur clairs, mise en page mobile). |
| Masquer un bouton selon le rôle, c'est de la sécurité ? | Non, c'est du confort. Le serveur refuse l'action (403) même si l'appel est forgé ; testé côté backend et par le parcours `/forbidden`. |

## 8. Plan d'apprentissage

| Étape | Durée | Objectif |
|---|---|---|
| 1 | 1 h | Utiliser l'application avec les 3 comptes de démo : refaire à la main les 13 parcours. |
| 2 | 2 h | Lire `playwright.config.ts`, `auth.setup.ts`, `journeys.spec.ts` ; relier chaque test au parcours fait à la main. |
| 3 | 1 h | Lancer `ui`, ouvrir le rapport HTML et un fichier `a11y/*.json`. |
| 4 | 1 h | Lire `RequireRole.tsx` et deux tests Vitest ; lancer `npm test`. |
| 5 | 1 h | Réciter les réponses du guide de soutenance (membre 2) et de la section 7. |

## 9. Auto-évaluation

- [ ] Je cite les 13 parcours E2E et les 4 navigateurs.
- [ ] J'explique le calcul 62 = 13 × 4 + 6 + 4, et les tests « skipped ».
- [ ] J'explique pourquoi Firefox n'a pas de résultat, et la procédure pour l'exécuter ailleurs.
- [ ] Je lance la suite et j'ouvre le rapport HTML sans aide.
- [ ] J'explique la différence entre un test Vitest et un test Playwright, avec un exemple de chaque.
- [ ] Je dis ce que vérifie axe-core et ce qu'il ne vérifie pas.
