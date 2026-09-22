# SmartRecruit — Frontend

SPA React consommant l'API via le gateway du dépôt `smartrecruit-backend` (`api-gateway`).

## Stack
- React 19, TypeScript, Vite
- React Router
- Axios

## Lancer en local

```bash
npm install
cp .env.example .env
npm run dev
```

L'app tourne sur `http://localhost:5173` et appelle l'API Gateway (`VITE_API_BASE_URL`, par défaut `http://localhost:8080/api/v1`).

## Structure

```
src/
  api/          # client HTTP (axios) + appels par domaine
  components/
    layout/     # layout applicatif (nav, shell)
  pages/
    auth/       # login, register
    candidate/  # profil, recherche d'offres, score de matching
    recruiter/  # profil entreprise, classement des candidatures
    admin/      # dashboard admin
  hooks/
  types/
```

## Pages (MVP — slide 19 du dossier)

| Page | Route | Statut |
| --- | --- | --- |
| Connexion / Inscription | `/login`, `/register` | à implémenter (Sprint 1) |
| Profil candidat + upload CV | `/candidate/profile` | à implémenter (Sprint 2-3) |
| Recherche / candidature | `/candidate/jobs` | à implémenter (Sprint 2-3) |
| Score de compatibilité expliqué | `/candidate/match/:jobId` | à implémenter (Sprint 4-5) |
| Profil entreprise / offres | `/recruiter/company` | à implémenter (Sprint 2-3) |
| Classement IA des candidatures | `/recruiter/jobs/:jobId/candidates` | à implémenter (Sprint 4-5) |
| Dashboard admin | `/admin` | à implémenter (Sprint 6) |
