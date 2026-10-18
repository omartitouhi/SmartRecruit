# SmartRecruit — Frontend

SPA React consommant l'API via le gateway du dépôt `smartrecruit-backend` (`api-gateway`).

> **Structures de données partagées :** avant de créer un type TypeScript pour une entité
> ou une réponse API, consultez `DATA_MODEL.md` dans `smartrecruit-backend` — c'est la
> référence commune aux 3 dépôts (noms de champs, enums, contrats IA comme `MatchScore`).

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
    ui/         # Button, Field, Tag, ScoreBreakdown, TagInput, états (vide, erreur, chargement)
    domain/     # composants métier partagés
  lib/          # formatage FR, erreurs API, useAsync, useReveal
  pages/
    auth/       # connexion, inscription, mot de passe oublié / réinitialisation
    candidate/  # offres, score expliqué, candidatures, profil + CV
    recruiter/  # entreprise et offres, nouvelle offre, classement IA
    admin/      # tableau de bord + journal d'audit
  styles/       # tokens, base, layout, composants, pages
  types/        # miroir de DATA_MODEL.md
```

## Pages

| Page | Route | Backend |
| --- | --- | --- |
| Accueil | `/` | — |
| Connexion / Inscription / Mot de passe | `/login`, `/register`, `/forgot-password`, `/reset-password` | ✅ auth-service |
| Profil candidat + upload CV + suppression RGPD | `/candidate/profile` | ✅ user-service |
| Offres recommandées + recherche | `/candidate/jobs` | ✅ recommandations · ⏳ recherche |
| Score de compatibilité expliqué | `/candidate/match/:jobId` | ⏳ ai-service / application-service |
| Mes candidatures | `/candidate/applications` | ⏳ application-service |
| Entreprise et offres | `/recruiter/company` | ⏳ user-service (entreprise) / job-service |
| Nouvelle offre | `/recruiter/jobs/new` | ⏳ job-service |
| Classement IA des candidatures | `/recruiter/jobs/:jobId/candidates` | ✅ profils candidats · ⏳ candidatures |
| Tableau de bord admin | `/admin` | ⏳ dashboard-service |

✅ endpoint existant · ⏳ endpoint à créer : ces écrans fonctionnent en mode démonstration.

## Contrats à confirmer avec le backend

Les endpoints suivants n'existent pas encore. Le frontend les appelle avec les chemins ci-dessous
(conventions de `DATA_MODEL.md`) ; à valider ou ajuster dans `src/api/` puis dans `DATA_MODEL.md`.

| Appel | Utilisé par |
| --- | --- |
| `GET /jobs?q=&location=&contractType=&experienceLevel=&companyId=&page=&size=` → `PaginatedResponse<Job & { companyName }>` | recherche, offres du recruteur |
| `GET /jobs/{id}` → `Job & { companyName }` | score expliqué, classement |
| `POST /jobs` (`JobCreateRequest`, DTO existant) · `PATCH /jobs/{id}/status` `{ status }` | nouvelle offre, publier / clôturer |
| `GET /users/companies/me` · `PUT /users/companies/me` | entreprise du recruteur |
| `POST /applications` `{ jobId }` · `GET /applications/me` · `GET /applications?jobId=` · `PATCH /applications/{id}/status` `{ status }` | candidatures |
| `POST /ai/matching/score` `{ candidateId, jobId }` → `MatchScore` (corps à confirmer) | score avant de postuler |
| `GET /dashboard/stats` → `DashboardStats` (forme proposée dans `src/api/dashboard.ts`) · `GET /dashboard/audit-logs?page=&size=` | tableau de bord |

## Crédits

Photo de l'accueil : « TunisAveHabibBourguiba » par Dacoslett, CC0, via
[Wikimedia Commons](https://commons.wikimedia.org/w/index.php?curid=13616433).
