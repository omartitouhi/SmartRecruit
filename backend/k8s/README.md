# SmartRecruit sur Kubernetes

Manifestes [Kustomize](https://kustomize.io) de toute la plateforme (6 services Java, service IA, frontend,
Postgres). `docker-compose.yml` reste l'outil de développement au quotidien ; ce dossier sert à tester
le déploiement sur un cluster et à préparer la production.

```
k8s/
  base/            Deployments, Services, ConfigMap, Postgres, volume des CV (commun à tous les environnements)
  overlays/dev/    cluster local kind : images locales, NodePorts, secrets de développement
  overlays/prod/   images ghcr.io, Ingress, autoscaling, budgets de disponibilité, NetworkPolicies
  kind-config.yaml cluster kind (ports 8090, 8080, 8001 publiés sur la machine)
  scripts/dev.sh   up | load | status | logs <service> | down
```

## Développement local (kind)

Prérequis : Docker, [kind](https://kind.sigs.k8s.io), `kubectl`. Arrêter `docker compose` (mêmes ports).

```bash
k8s/scripts/dev.sh up
```

Le script crée le cluster, construit les images manquantes (`docker compose build`), les charge dans kind,
déploie l'overlay `dev` et attend que tout soit prêt. La clé OpenRouter est reprise de `.env` (celui de
docker compose) ; sinon, la renseigner dans `k8s/overlays/dev/secrets.env` (ignoré par git).

| Adresse | Service |
|---|---|
| http://localhost:8090 | interface |
| http://localhost:8080 | API (gateway) |
| http://localhost:8001/docs | service IA |

Après une modification du code : `docker compose build <service>` puis `k8s/scripts/dev.sh load`.
`k8s/scripts/dev.sh down` supprime le cluster (et ses données).

## Production

1. Publier les images : les workflows `images.yml` des trois dépôts les poussent sur `ghcr.io` (dépôts privés :
   créer un `imagePullSecret` ou rendre les paquets lisibles par le cluster).
2. Installer `ingress-nginx` et `cert-manager` (ClusterIssuer `letsencrypt`) ; remplacer le nom d'hôte dans
   `overlays/prod/ingress.yaml`.
3. Créer les secrets **avant** le déploiement (ils ne sont jamais dans le dépôt) :

```bash
kubectl create namespace smartrecruit
kubectl -n smartrecruit create secret generic app-secrets \
  --from-literal=DB_USER=... --from-literal=DB_PASSWORD=... \
  --from-literal=JWT_SECRET=... --from-literal=INTERNAL_AUTH_SECRET=... \
  --from-literal=CV_ENCRYPTION_KEY="$(openssl rand -base64 32)" \
  --from-literal=ADMIN_EMAIL=... --from-literal=ADMIN_PASSWORD=... \
  --from-literal=OPENROUTER_API_KEY=...
```

   (ou Sealed Secrets / External Secrets). Les secrets de développement ne doivent jamais servir en production.
4. Figer les versions puis déployer :

```bash
cd k8s/overlays/prod
kustomize edit set image ghcr.io/omartitouhi/smartrecruit-api-gateway=ghcr.io/omartitouhi/smartrecruit-api-gateway:<sha>
kubectl apply -k .
```

## Limites connues (choix du MVP, à revoir avant une vraie production)

- **Un seul `user-service`** : les CV chiffrés sont sur un volume `ReadWriteOnce`. Plusieurs exemplaires exigent un stockage objet.
- **Postgres dans le cluster, sans réplication** (StatefulSet). En production : service managé ou opérateur (CloudNativePG).
- **Schéma de base** : `ddl-auto: update`, sans migrations. Plusieurs exemplaires qui démarrent en même temps peuvent se
  disputer la création des tables : déployer d'abord avec une réplique, ou ajouter Flyway.
- **Limite de requêtes du gateway en mémoire** : elle s'applique par exemplaire de gateway.
- **NetworkPolicies** : sans règle de sortie, et non vérifiables sur kind (son CNI ne les applique pas).
- **Pas de métriques Prometheus** (`/actuator/prometheus` demande une dépendance de plus).
- Le frontend (nginx) tourne en root : image officielle `nginx:alpine`.
