# SmartRecruit — DevOps

Ce document relie chaque notion du cours DevOps (Kubernetes, Services, Ingress, Helm, ConfigMaps/Secrets, PV/PVC,
Terraform, Ansible, Prometheus/Grafana, ELK) à son implémentation dans le projet, avec les commandes pour la démontrer.

```
                       ┌──────────────── Ansible (ansible/) ─────────────────┐
                       │ prépare la machine : Docker, kubectl, kind, helm,   │
                       │ terraform, clone des dépôts, puis lance Terraform   │
                       └──────────────────────────┬──────────────────────────┘
                                                  ▼
 ┌────────────────────────────── Terraform (terraform/) ──────────────────────────────┐
 │ module kind-cluster      cluster kind (ports 80/443 -> Ingress)                    │
 │ module platform-addons   ingress-nginx · metrics-server · kube-prometheus-stack    │
 │ module smartrecruit      images locales -> kind · chart smartrecruit · chart elk   │
 └────────────────────────────────────────────────────────────────────────────────────┘
        │                              │                                │
        ▼                              ▼                                ▼
 namespace smartrecruit         namespace monitoring             namespace logging
 (helm/smartrecruit)            (kube-prometheus-stack)          (helm/elk)
 frontend, api-gateway,         Prometheus ◄── ServiceMonitors   Filebeat (DaemonSet)
 6 services, ai-service,        Grafana   ◄── dashboard          └► Logstash └► Elasticsearch
 Postgres (StatefulSet)         Alertmanager ◄── PrometheusRule                 └► Kibana
```

| Dossier | Contenu |
|---|---|
| `*/Dockerfile`, `docker-compose.yml` | Images des 8 composants, développement local |
| `k8s/` | Manifestes Kustomize (base + overlays dev/prod), cluster kind, `scripts/dev.sh` |
| `helm/smartrecruit/` | Chart Helm de la plateforme (mêmes ressources que `k8s/`, plus monitoring) |
| `helm/elk/` | Chart Helm de la stack de logs (Elasticsearch, Logstash, Kibana, Filebeat) — [README](helm/elk/README.md) |
| `monitoring/` | Valeurs du chart kube-prometheus-stack (Prometheus, Grafana, Alertmanager) |
| `terraform/` | Infrastructure as Code : cluster + toutes les releases Helm — [README](terraform/README.md) |
| `ansible/` | Provisionnement d'une machine Ubuntu et déploiement — [README](ansible/README.md) |
| `.github/workflows/` | CI : tests, images ghcr.io, validation Kustomize (`k8s.yml`) et IaC (`infra.yml`) |

## Correspondance cours → projet

| Notion du cours | Où dans le projet |
|---|---|
| Conteneurs, images | 8 `Dockerfile` (multi-stage pour Java et frontend, utilisateur non root UID 10001 pour Java et IA), `.dockerignore` |
| Pod, ReplicaSet, Deployment | `helm/smartrecruit/templates/deployments.yaml` (une boucle `range` sur `values.services`), `k8s/base/*.yaml` |
| Stratégies RollingUpdate / Recreate | `defaultStrategy` (RollingUpdate `maxSurge: 1`, `maxUnavailable: 0`) ; `user-service` en `Recreate` (volume RWO) |
| Rollout / rollback, historique | `revisionHistoryLimit: 10` ; voir [Rollout et rollback](#rollout-et-rollback) |
| readinessProbe / livenessProbe / startupProbe | Tous les conteneurs (actuator `health/liveness` et `health/readiness`, `/api/v1/ai/ready`) |
| Labels et selectors | `app.kubernetes.io/name` + `instance` (sélecteurs), `tier` (NetworkPolicies) |
| Service ClusterIP | `templates/services.yaml` (type par défaut) |
| Service NodePort | Overlay Kustomize `k8s/overlays/dev` (30080, 30090, 30001) ; chart : `serviceType: NodePort` + `nodePort` |
| Service headless | `postgres` (`clusterIP: None`) pour le StatefulSet |
| Ingress NGINX (fanout, host virtuel, TLS) | `templates/ingress.yaml` : `/api` → api-gateway, `/` → frontend ; dev `smartrecruit.local`, prod TLS cert-manager ; Kibana et Grafana sur leurs propres hôtes |
| Namespace | `smartrecruit`, `monitoring`, `logging` |
| ConfigMap (env, envFrom, volume) | `<release>-config` injectée par `envFrom` ; script d'init Postgres monté en volume ; pipeline Logstash et `filebeat.yml` montés en fichiers |
| Secret (`b64enc`, `secretKeyRef`) | `templates/secret.yaml` (`b64enc`, génération aléatoire + `lookup` pour garder les valeurs), injecté clé par clé |
| PV / PVC, StorageClass, accessModes, reclaim | PVC `cv-data` (RWO, `helm.sh/resource-policy: keep`) ; `volumeClaimTemplates` de Postgres, Elasticsearch, Prometheus ; StorageClass par défaut = provisionnement dynamique |
| StatefulSet | Postgres, Elasticsearch, Prometheus (nom stable + volume dédié) |
| DaemonSet | Filebeat (`helm/elk`), node-exporter (kube-prometheus-stack) : un pod par nœud |
| HPA, PDB, NetworkPolicy | `hpa.yaml`, `pdb.yaml`, `networkpolicies.yaml` (activés dans `values-prod.yaml`) |
| Helm : chart, `values.yaml`, `_helpers.tpl`, NOTES, tests, dev/prod | `helm/smartrecruit/` et `helm/elk/` ; `values-dev.yaml` / `values-prod.yaml` ; `helm test` |
| Terraform : providers, ressources, variables + `validation`, `locals`, `for`/`for_each`/`count`, conditions, outputs, modules, backend local, tfvars | `terraform/` (3 modules, `envs/dev.tfvars`, `envs/prod.tfvars`, backend `state/terraform.tfstate`) |
| Ansible : inventaire, groupes, `ansible.cfg`, playbook, `become`, `vars_files`, modules apt/template/service, `notify`/handlers, rôles, Jinja2, vault | `ansible/` (rôles `common`, `docker`, `k8s_tools`, `smartrecruit`) |
| Prometheus : pull/scraping, PromQL, Counter / Gauge / Histogram | `/actuator/prometheus` (Java), `/metrics` (IA), ServiceMonitors, PrometheusRule |
| Grafana | Tableau de bord « SmartRecruit - Vue d'ensemble » (`helm/smartrecruit/files/smartrecruit-dashboard.json`) |
| ELK + Filebeat | `helm/elk/` : Filebeat → Logstash (input/filter/output) → Elasticsearch → Kibana |

## Démarrage

Quatre façons de lancer la plateforme, de la plus légère à la plus complète. Toutes partent des images locales
construites par `docker compose build` (dans ce dépôt, avec `../smartrecruit-frontend` et `../smartrecruit-ia` à côté).

| Méthode | Commande | Contenu |
|---|---|---|
| Docker Compose | `docker compose up -d` | Développement au quotidien, sans Kubernetes |
| Kustomize sur kind | `k8s/scripts/dev.sh up` | Plateforme sur Kubernetes, accès par NodePorts |
| Helm | voir ci-dessous | Chart sur un cluster existant (minikube, kind...) |
| Terraform | `cd terraform && terraform init && terraform apply -var-file=envs/dev.tfvars` | Cluster kind + Ingress + monitoring + logs + application |
| Ansible | `cd ansible && ansible-playbook site.yml -K --ask-vault-pass` | Machine Ubuntu vierge → tout ce qui précède via Terraform |

Avec Terraform ou Ansible, ajouter au fichier hosts (`/etc/hosts` ou `C:\Windows\System32\drivers\etc\hosts`) :

```
127.0.0.1 smartrecruit.local grafana.smartrecruit.local kibana.smartrecruit.local
```

| Adresse | Service |
|---|---|
| http://smartrecruit.local | Interface (et `/api` vers le gateway) |
| http://grafana.smartrecruit.local | Grafana (utilisateur `admin`, mot de passe : `terraform output grafana_admin_password_command`) |
| http://kibana.smartrecruit.local | Kibana (Discover, data view `smartrecruit-*`) |

## Helm

```bash
# Rendu sans installer (vérifie templates et valeurs)
helm install --dry-run --debug smartrecruit ./helm/smartrecruit -n smartrecruit -f helm/smartrecruit/values-dev.yaml

# Installation / mise à jour (dev : images locales, Ingress smartrecruit.local, secrets de dev)
helm upgrade --install smartrecruit ./helm/smartrecruit -n smartrecruit --create-namespace \
  -f helm/smartrecruit/values-dev.yaml

helm list -n smartrecruit
helm get manifest smartrecruit -n smartrecruit      # manifests réellement appliqués
helm test smartrecruit -n smartrecruit              # pod de test : gateway, IA et frontend répondent

# Mise à jour d'une valeur puis retour arrière
helm upgrade smartrecruit ./helm/smartrecruit -n smartrecruit -f helm/smartrecruit/values-dev.yaml \
  --set services.job-service.replicas=3
helm history smartrecruit -n smartrecruit
helm rollback smartrecruit 1 -n smartrecruit

# Paquet du chart
helm package helm/smartrecruit
```

Points notables du chart :
- **Un seul template par type d'objet** : `deployments.yaml` et `services.yaml` bouclent sur `values.services` ; ajouter un
  service = ajouter une entrée dans `values.yaml`.
- **`_helpers.tpl`** : noms, labels communs, sélecteurs, image `<registry>/<prefix><service>:<tag>`, référence de secret.
- **Secrets** : `secrets.create: true` génère les valeurs vides au premier `install` puis les relit (`lookup`) aux
  `upgrade` suivants ; en production `secrets.create: false` + `existingSecret: app-secrets` (créé hors dépôt).
- **`checksum/config`** : les pods redémarrent quand la ConfigMap change (`helm upgrade`).
- **Production** (`values-prod.yaml`) : images ghcr.io, 2 réplicas, TLS, HPA, PDB, NetworkPolicies, monitoring.

## Rollout et rollback

Démonstration de l'exercice 2 de l'examen sur un Deployment de SmartRecruit :

```bash
NS=smartrecruit
# Toute modification du template de pod crée une révision (ici une variable d'environnement ;
# en vrai : kubectl set image deploy/job-service job-service=<registre>/smartrecruit-job-service:<sha>)
kubectl -n $NS set env deploy/job-service DEMO_REVISION=2                              # révision 2
kubectl -n $NS annotate deploy/job-service kubernetes.io/change-cause="DEMO_REVISION=2" --overwrite
kubectl -n $NS rollout status deploy/job-service
kubectl -n $NS rollout history deploy/job-service
kubectl -n $NS get rs -l app.kubernetes.io/name=job-service      # un ReplicaSet par révision (pod-template-hash)
kubectl -n $NS rollout undo deploy/job-service                    # nouvelle révision = copie de la précédente
kubectl -n $NS rollout undo deploy/job-service --to-revision=1
```

- RollingUpdate (`maxSurge: 1`, `maxUnavailable: 0`) : un nouveau pod doit être *Ready* (readinessProbe) avant qu'un
  ancien soit arrêté, donc aucune interruption.
- Recreate (`user-service`) : tous les anciens pods s'arrêtent avant le démarrage des nouveaux. C'est obligatoire ici,
  car le volume des CV est en ReadWriteOnce.
- Supprimer un ancien ReplicaSet efface sa révision de `rollout history` ; supprimer le ReplicaSet courant le fait
  recréer à l'identique (même hash de template).
- Avec Helm, `helm rollback` revient à une révision de *release* (tous les objets du chart), là où
  `kubectl rollout undo` ne revient que sur un Deployment.

## Stockage (PV / PVC)

```bash
kubectl get storageclass                 # (default) : provisionnement dynamique
kubectl -n smartrecruit get pvc          # smartrecruit-cv-data, data-postgres-0
kubectl get pv                           # PV créés automatiquement, reclaimPolicy Delete
```

- `smartrecruit-cv-data` (ReadWriteOnce, 1 Gi en dev) : CV chiffrés de `user-service`, monté sur `/data/cvs`.
  L'annotation `helm.sh/resource-policy: keep` le conserve après un `helm uninstall`.
- `volumeClaimTemplates` : un PVC par pod de StatefulSet (`data-postgres-0`, Elasticsearch, Prometheus).
- Sans `storageClassName`, le PVC prend la StorageClass par défaut (dynamique) ; pour lier un PV statique il faut
  `storageClassName: ""` et éventuellement `claimRef`, comme dans le TP PV/PVC.

## Terraform

Détails : [terraform/README.md](terraform/README.md).

```bash
cd terraform
terraform init
terraform plan  -var-file=envs/dev.tfvars
terraform apply -var-file=envs/dev.tfvars
terraform output urls
terraform destroy -var-file=envs/dev.tfvars
```

- **Providers** : `tehcyx/kind`, `hashicorp/helm`, `hashicorp/kubernetes`, `hashicorp/null`, `hashicorp/random`.
- **Variables validées** : par exemple `environment` ∈ {dev, prod}, ports entre 1 et 65535, `admin_password` d'au moins
  12 caractères.
- **Secrets générés** : `random_password` et `random_bytes` (clé AES de 32 octets).
- **Boucles** : `for_each` sur les images à charger dans kind, `count` pour les composants optionnels, `for` pour les URLs.
- **Backend local** : `state/terraform.tfstate`, ignoré par git, comme le TP5. Étape suivante : S3 + verrou DynamoDB,
  ou Terraform Cloud.

## Ansible

Détails : [ansible/README.md](ansible/README.md). La machine de contrôle doit être sous Linux ou WSL (Ansible ne tourne
pas nativement sous Windows).

```bash
cd ansible
ansible-galaxy collection install -r requirements.yml
ansible all -m ping
cp inventory/group_vars/all/vault.yml.example inventory/group_vars/all/vault.yml
ansible-vault encrypt inventory/group_vars/all/vault.yml
ansible-playbook site.yml -K --ask-vault-pass --check --diff   # simulation
ansible-playbook site.yml -K --ask-vault-pass
ansible-playbook site.yml -K --ask-vault-pass --tags deploy    # seulement le déploiement
```

`smartrecruit_deploy_method` choisit la suite : `terraform` (par défaut), `compose` ou `kind-script`.

## Monitoring : Prometheus et Grafana

Les services exposent leurs métriques, et Prometheus vient les lire (modèle *pull*) grâce aux ServiceMonitors du chart :

| Service | Endpoint | Bibliothèque |
|---|---|---|
| 6 services Spring Boot | `/actuator/prometheus` | Micrometer (`micrometer-registry-prometheus`) |
| Service IA | `/metrics` | `prometheus-fastapi-instrumentator` + `prometheus_client` |

Exemples de métriques par type (cf. exercice 1 de l'examen) :

| Métrique | Type | Description |
|---|---|---|
| `http_server_requests_seconds_count` / `http_requests_total` | Counter | Nombre total de requêtes HTTP (Java / IA) |
| `http_server_requests_seconds_bucket` / `http_request_duration_seconds_bucket` | Histogram | Durée des requêtes, par buckets |
| `smartrecruit_ai_match_requests_total{engine,outcome}` | Counter | Calculs de score de compatibilité |
| `smartrecruit_ai_match_score` | Histogram | Répartition des scores (0 à 100) |
| `jvm_memory_used_bytes`, `process_cpu_usage` | Gauge | Mémoire heap, CPU instantanés |
| `smartrecruit_ai_llm_calls_in_progress` | Gauge | Appels OpenRouter en cours |
| `up` | Gauge | 1 si la cible répond au scraping |

Requêtes PromQL utiles :

```promql
sum by (service) (rate(http_server_requests_seconds_count{namespace="smartrecruit"}[5m]))                 # req/s
histogram_quantile(0.95, sum by (le, service) (rate(http_server_requests_seconds_bucket[5m])))            # p95
sum by (service) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  / sum by (service) (rate(http_server_requests_seconds_count[5m]))                                       # erreurs
```

- **Installation** : par Terraform (`enable_monitoring = true`), ou à la main :
  `helm upgrade --install monitoring prometheus-community/kube-prometheus-stack -n monitoring --create-namespace -f monitoring/kube-prometheus-stack-values.yaml`,
  puis le chart smartrecruit avec `--set monitoring.enabled=true`.
- **Accès** : http://grafana.smartrecruit.local, ou
  `kubectl -n monitoring port-forward svc/monitoring-grafana 3000:80` et
  `kubectl -n monitoring port-forward svc/monitoring-kube-prometheus-prometheus 9090` (onglet *Status → Targets*).
- **Alertes** (PrometheusRule `smartrecruit-alerts`) : service injoignable, pods qui redémarrent en boucle, taux de 5xx
  au-dessus de 5 %, latence p95 au-dessus de 1 s, heap JVM au-dessus de 90 %.

## Logs : ELK

Détails : [helm/elk/README.md](helm/elk/README.md).

- **Filebeat** (DaemonSet) lit `/var/log/containers/*_smartrecruit_*.log` et ajoute les métadonnées Kubernetes.
- **Logstash** reçoit les logs (beats sur 5044) et les filtre :
  - `json` pour les logs ECS des services Spring (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`, posé par le chart) ;
  - `grok %{COMBINEDAPACHELOG}` pour nginx ;
  - `grok` sur le niveau de log pour uvicorn.
- **Elasticsearch** stocke un index par jour (`smartrecruit-AAAA.MM.JJ`).
- **Kibana** affiche le tout ; un Job Helm crée la data view `smartrecruit-*`.
- Exemple de requête KQL dans Discover : `service.name : "auth-service" and log.level : "ERROR"`.
- Avec `logstash.enabled=false`, Filebeat écrit directement dans Elasticsearch : c'est le compromis
  « Filebeat ou Logstash ? » vu en cours.

## CI

| Workflow | Vérifie |
|---|---|
| `ci.yml` (3 dépôts) | Build et tests (Maven, Vitest, pytest + ruff) |
| `images.yml` (3 dépôts) | Publication des images sur ghcr.io (`sha`, `latest`, tags `v*`) |
| `k8s.yml` | Rendu des overlays Kustomize + kubeconform |
| `infra.yml` | `helm lint` / `helm template` + kubeconform (CRDs comprises), `terraform fmt` + `validate`, `ansible-playbook --syntax-check` + ansible-lint (informatif) |

## Limites connues

- ELK en mode lab : nœud Elasticsearch unique, sécurité X-Pack désactivée. En production : ECK, ou un service managé
  avec TLS et authentification.
- Le déploiement en production n'est pas automatisé par la CI (images publiées, déploiement par Helm/Terraform à la main).
- Alertmanager n'a pas de destinataire configuré (Slack, e-mail) : les alertes restent visibles dans son interface.
- Les autres limites de la plateforme sur Kubernetes (Postgres unique, volume RWO des CV...) sont listées dans
  [k8s/README.md](k8s/README.md).
