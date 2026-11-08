# SmartRecruit

Plateforme de recrutement assistée par IA : les candidats déposent leur CV, les recruteurs publient des offres, et un
service IA calcule un score de compatibilité expliqué critère par critère.

Monorepo : un seul dépôt pour le code, l'infrastructure et la documentation.

```
SmartRecruit/
├── backend/             6 microservices Spring Boot (gateway, auth, user, job, application, dashboard), e2e, seed
├── frontend/            SPA React (Vite) servie par nginx
├── ai-service/          Service IA FastAPI (parsing de CV, matching, recommandations, compétences manquantes)
├── deploy/
│   ├── k8s/             Manifestes Kustomize (base + overlays dev/prod) et cluster kind
│   ├── helm/            Charts Helm : smartrecruit (plateforme + monitoring) et elk (logs)
│   ├── monitoring/      Valeurs de kube-prometheus-stack (Prometheus, Grafana, Alertmanager)
│   ├── terraform/       Infrastructure as Code : cluster kind + toutes les releases Helm
│   └── ansible/         Provisionnement d'une machine Ubuntu et déploiement
├── docs/                DATA_MODEL.md, API_CONTRACTS.md, DEVOPS.md
├── docker-compose.yml   Toute la stack en local
└── .github/workflows/   CI par composant, images ghcr.io, validation de deploy/
```

## Lancer toute la stack

Prérequis : Docker (avec Compose).

```bash
cp .env.example .env
docker compose up --build
```

| Adresse | Service |
|---|---|
| http://localhost:5173 | Interface (ou `FRONTEND_HOST_PORT`) |
| http://localhost:8080 | API (gateway) |
| http://localhost:8000/docs | Service IA (Swagger) |

Un compte administrateur de développement est créé au démarrage (`ADMIN_EMAIL` / `ADMIN_PASSWORD` dans `.env`). Par
défaut le service IA utilise un moteur à règles ; renseigner `OPENROUTER_API_KEY` et `AI_ENGINE=llm` pour passer par
un LLM. Données de démonstration : `python backend/seed/seed_demo.py` (voir [backend/README.md](backend/README.md)).

## Déployer

| Méthode | Commande | Contenu |
|---|---|---|
| Kustomize sur kind | `deploy/k8s/scripts/dev.sh up` | Plateforme sur Kubernetes, accès par NodePorts |
| Helm | `helm upgrade --install smartrecruit ./deploy/helm/smartrecruit -n smartrecruit --create-namespace -f deploy/helm/smartrecruit/values-dev.yaml` | Chart sur un cluster existant |
| Terraform | `cd deploy/terraform && terraform init && terraform apply -var-file=envs/dev.tfvars` | Cluster kind + Ingress + Prometheus/Grafana + ELK + application |
| Ansible | `cd deploy/ansible && ansible-playbook site.yml -K --ask-vault-pass` | Machine Ubuntu vierge (VM cloud) → tout ce qui précède |
| VM cloud (Azure...) | `deploy/scripts/deploy-vm.sh azureuser@<ip> -i <clé.pem>` | Copie le code par SSH et lance Ansible sur la VM : http://&lt;ip&gt;.nip.io |

Détails, correspondance avec le cours DevOps et commandes de démonstration : [docs/DEVOPS.md](docs/DEVOPS.md).

## Documentation

| Document | Contenu |
|---|---|
| [docs/DATA_MODEL.md](docs/DATA_MODEL.md) | Structures de données, enums et contrats partagés (référence commune aux 3 composants) |
| [docs/API_CONTRACTS.md](docs/API_CONTRACTS.md) | Contrats d'API entre services et vers le frontend |
| [docs/DEVOPS.md](docs/DEVOPS.md) | Docker, Kubernetes, Helm, Terraform, Ansible, Prometheus/Grafana, ELK, CI |
| [backend/README.md](backend/README.md) | Architecture des microservices, sécurité, tests de bout en bout |
| [frontend/README.md](frontend/README.md) | Application React, mode mock, structure |
| [ai-service/README.md](ai-service/README.md) | Moteurs règles / LLM, endpoints IA, métriques |

## Historique

Ce dépôt réunit les anciens dépôts `smartrecruit-backend`, `smartrecruit-frontend` et `smartrecruit-ia`, avec tout
leur historique git (chaque dépôt réécrit dans son sous-dossier). `git log -- backend/` montre l'historique du
backend ; `git log --follow` suit les fichiers déplacés vers `deploy/` et `docs/`.
