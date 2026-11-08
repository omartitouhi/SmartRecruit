# SmartRecruit — Frontend

SPA React consommant l'API via le gateway du monorepo (`api-gateway`).

> **Structures de données partagées :** avant de créer un type TypeScript pour une entité
> ou une réponse API, consultez `DATA_MODEL.md` dans `docs/` — c'est la
> référence commune aux 3 composants (noms de champs, enums, contrats IA comme `MatchScore`).

## Stack
- React 19, TypeScript, Vite
- React Router
- Axios
- CSS sans framework (tokens dans `src/styles/tokens.css`)

## Lancer en local

```bash
npm install
cp .env.example .env
npm run dev
```

L'app tourne sur `http://localhost:5173`. Les appels partent vers `/api/v1` et Vite les relaie
vers le gateway (`VITE_PROXY_TARGET`, par défaut `http://localhost:8080`) : aucune configuration
CORS n'est nécessaire côté backend. En Docker, `nginx.conf` fait la même chose vers `api-gateway:8080`.

### Mode démonstration (sans backend)

```bash
VITE_USE_MOCKS=true npm run dev
```

Toutes les données viennent de `src/api/mock/` (offres, candidats, scores expliqués calculés dans
le navigateur), avec les mêmes règles que le backend (rôles, transitions de statut, limites du CV).
Comptes : `candidat@demo.tn`, `recruteur@demo.tn`, `admin@demo.tn` — mot de passe `demo1234`.
Un bandeau rappelle que les données sont fictives ; « Réinitialiser les données » repart du jeu initial.

Pour ne simuler que certains modules pendant que les autres appellent le vrai backend :

```bash
VITE_MOCK_MODULES=jobs,applications npm run dev
```

Modules : `auth`, `candidates`, `companies`, `jobs`, `applications`, `dashboard`. `VITE_USE_MOCKS=true` les
simule tous. Avec l'authentification réelle, les modules simulés lisent le rôle et l'identifiant dans le JWT.
Les modules simulés partagent une même base dans le navigateur : simulez ensemble ceux qui dépendent l'un de
l'autre (`jobs` avec `companies`, `applications` avec `jobs` et `candidates`).
En Docker : `--build-arg VITE_USE_MOCKS=true` ou `--build-arg VITE_MOCK_MODULES=jobs,applications`.

## Design

Direction « Le dossier » (skill taste-skill `minimalist-ui`) : fond blanc cassé, titres en
Newsreader, interface en Geist, couleur réservée aux statuts. Le composant central est
`ScoreBreakdown` : un score toujours accompagné de son explication, critère par critère.

## Structure

```
src/
  api/          # client axios (token + refresh), un module par service, mock/ pour la démo
  auth/         # session (AuthProvider), garde de rôle (RequireRole, GuestOnly)
  components/
    layout/     # barre publique, barre applicative par rôle, pied de page
    ui/         # Button, Field, Tag, ScoreBreakdown, TagInput, Pager, états (vide, erreur, chargement)
    domain/     # composants métier partagés (ApplyButton, JobMeta, NoCompany)
  lib/          # formatage FR, erreurs API, useAsync, storage
  pages/
    auth/       # connexion, inscription, mot de passe oublié / réinitialisation
    candidate/  # tableau de bord, offres, détail, score expliqué, recommandations, candidatures, profil + CV
    recruiter/  # tableau de bord, mes offres, publication, candidatures reçues, entreprise, équipe, invitation
    admin/      # tableau de bord, utilisateurs, modération des offres, journal d'audit
  styles/       # tokens, base, layout, composants, pages
  test/         # aides de test (rendu, fixtures, base de démonstration)
  types/        # miroir de DATA_MODEL.md et API_CONTRACTS.md
```

## Pages

| Page | Route | Rôle | Tickets |
| --- | --- | --- | --- |
| Accueil | `/` | public | — |
| Connexion / Inscription / Mot de passe | `/login`, `/register`, `/forgot-password`, `/reset-password` | public | FE-AUTH-01, 02, 05 |
| Accès refusé | `/forbidden` | tous | FE-AUTH-04 |
| Tableau de bord candidat | `/candidate` | candidat | FE-DASH-01 |
| Offres (recherche filtrée) et détail | `/candidate/jobs`, `/candidate/jobs/:jobId` | candidat | FE-JOB-02, 03, FE-APP-01 |
| Offres recommandées | `/candidate/recommendations` | candidat | FE-REC-01 |
| Score expliqué + compétences à développer | `/candidate/match/:jobId` | candidat | FE-MATCH-01, FE-REC-02 |
| Mes candidatures | `/candidate/applications` | candidat | FE-APP-02 |
| Profil (3 étapes), CV, données extraites | `/candidate/profile` | candidat | FE-USER-01 à 03, FE-CV-01 |
| Tableau de bord recruteur | `/recruiter` | recruteur | FE-DASH-02 |
| Mes offres | `/recruiter/jobs` | recruteur | FE-JOB-04 |
| Nouvelle offre | `/recruiter/jobs/new` | recruteur | FE-JOB-01 |
| Modifier une offre (brouillon ou publiée) | `/recruiter/jobs/:jobId/edit` | recruteur | FE-JOB-01 |
| Candidatures reçues, classées par score | `/recruiter/jobs/:jobId/candidates` | recruteur | FE-APP-03, FE-MATCH-02 |
| Entreprise | `/recruiter/company` | recruteur | FE-COMP-01 |
| Équipe et invitations | `/recruiter/team` | recruteur | FE-COMP-02 |
| Accepter une invitation | `/accept-invitation?token=` | recruteur | FE-COMP-02 |
| Tableau de bord admin | `/admin` | admin | — |
| Utilisateurs | `/admin/users` | admin | FE-ADMIN-01 |
| Modération des offres | `/admin/jobs` | admin | FE-ADMIN-02 |
| Journal d'audit | `/admin/audit-log` | admin | FE-ADMIN-03 |

## Contrats d'API

Les chemins appelés sont ceux de `docs/API_CONTRACTS.md`, qui fait foi ; les formes
de données sont dans `DATA_MODEL.md`. Un module par service dans `src/api/` (`auth`, `candidates`, `companies`,
`jobs`, `applications`, `dashboard`) ; `src/api/mock/` suit les mêmes règles (codes 400/403/404/409/410/422,
transitions de statut, 409 sur les doublons) pour que la démonstration se comporte comme le vrai backend.

Points à connaître :
- `cvAnalysisStatus` (`NONE`, `PENDING`, `DONE`, `FAILED`) remplace l'ancienne heuristique « CV sans date d'analyse ».
- Le score d'une offre avant candidature (`GET /applications/match-preview?jobId=`) exige un CV analysé (422 sinon).
- `acceptanceRate` du tableau de bord recruteur peut être une fraction (0,5) ou un pourcentage (50) : l'affichage accepte les deux.
- Les membres d'une entreprise ne sont que des identifiants de compte : la page équipe n'affiche donc pas leur email.

## Tests

```bash
npm test          # vitest + Testing Library (jsdom), une fois
npm run test:watch
```

Ils couvrent les modules d'API contre le contrat (méthode, chemin, paramètres, corps), l'intercepteur de
rafraîchissement du jeton, les règles métier de la couche de démonstration, les aides de `src/lib` et les
principales pages (formulaires, gardes de rôle, états du CV, recherche, candidature, statuts, administration).
La CI exécute `npm run lint`, `npm test` et `npm run build`.

## Crédits

Photo de l'accueil : « TunisAveHabibBourguiba » par Dacoslett, CC0, via
[Wikimedia Commons](https://commons.wikimedia.org/w/index.php?curid=13616433).
