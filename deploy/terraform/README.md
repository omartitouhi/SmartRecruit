# SmartRecruit avec Terraform

Un seul `terraform apply` crée un environnement complet :

1. un cluster Kubernetes local **kind** (dev) ou la connexion à un cluster existant (prod) ;
2. les composants de la plateforme : **ingress-nginx**, **metrics-server**, **kube-prometheus-stack** (Prometheus, Grafana, Alertmanager) ;
3. **SmartRecruit** (chart Helm `../helm/smartrecruit`) et la stack de logs **ELK** (chart `../helm/elk`).

```
deploy/terraform/
  versions.tf            version de Terraform, providers, backend local (state/terraform.tfstate)
  providers.tf           connexion kind (dev) ou kubeconfig (prod)
  variables.tf           variables d'entrée, avec règles de validation
  main.tf                locals, secrets générés, appel des 3 modules
  outputs.tf             URLs, commandes utiles, mots de passe (sensibles)
  envs/dev.tfvars        cluster kind + images locales
  envs/prod.tfvars       cluster existant + images ghcr.io + TLS
  terraform.tfvars.example
  modules/
    kind-cluster/        cluster kind prêt pour l'Ingress (label ingress-ready, ports 80/443 publiés)
    platform-addons/     ingress-nginx, metrics-server, kube-prometheus-stack (helm_release)
    smartrecruit/        chargement des images dans kind, namespace, releases smartrecruit et elk
```

## Prérequis

| Outil | Version | Windows (Chocolatey) |
|---|---|---|
| Terraform | >= 1.5 | `choco install terraform -y` |
| Docker | Docker Desktop | |
| kind | >= 0.20 | `choco install kind -y` |
| kubectl | | `choco install kubernetes-cli -y` |

Helm n'est pas nécessaire : le provider `helm` embarque son propre client.

Machine de dev : prévoir **8 Go de RAM libres pour Docker** si le monitoring et ELK sont activés
(sinon `-var enable_logging=false` ou `-var enable_monitoring=false`).

## Développement local (kind)

```bash
# 1. Construire les images (backend, service IA et frontend) et télécharger Postgres : depuis la racine du monorepo
docker compose build
docker compose pull postgres

# 2. Déployer
cd deploy/terraform
terraform init                      # télécharge les providers (kind, helm, kubernetes, null, random)
terraform fmt -check -recursive     # style
terraform validate                  # syntaxe et types
terraform plan  -var-file=envs/dev.tfvars
terraform apply -var-file=envs/dev.tfvars
```

Comptez 10 à 15 minutes au premier `apply` (téléchargement des images de Prometheus, Grafana, Elasticsearch...).

3. Ajouter les noms d'hôtes au fichier hosts (la ligne exacte est dans `terraform output hosts_file_line`) :

```
127.0.0.1 smartrecruit.local grafana.smartrecruit.local kibana.smartrecruit.local
```

- Linux / macOS / WSL : `/etc/hosts` (avec `sudo`)
- Windows : `C:\Windows\System32\drivers\etc\hosts` (éditeur lancé en administrateur)

4. Ouvrir les interfaces :

| URL | Service |
|---|---|
| http://smartrecruit.local | application (`/api` -> api-gateway) |
| http://grafana.smartrecruit.local | Grafana (utilisateur `admin`, mot de passe : `terraform output -raw grafana_admin_password_command` puis exécuter la commande) |
| http://kibana.smartrecruit.local | Kibana |
| `kubectl -n monitoring port-forward svc/monitoring-kube-prometheus-prometheus 9090` | Prometheus sur http://localhost:9090 |

Compte administrateur de l'application : `terraform output admin_email` et `terraform output -raw admin_password`.

Kubectl : kind ajoute le contexte `kind-smartrecruit-tf` au kubeconfig (`terraform output kube_context`).

```bash
kubectl config use-context kind-smartrecruit-tf
kubectl -n smartrecruit get pods
```

Le port 80 est déjà utilisé sur votre machine ? `-var http_port=8081 -var https_port=8443` (les URLs deviennent
`http://smartrecruit.local:8081`). Arrêter aussi `docker compose` et le cluster de `deploy/k8s/scripts/dev.sh` s'ils tournent.

### Images locales

Le module `smartrecruit` exécute `kind load docker-image <image> --name <cluster>` pour chaque image
(`null_resource` + `local-exec`, une ressource par image grâce à `for_each`). La commande n'utilise aucune syntaxe
propre à un shell : elle tourne sous `/bin/sh` (Linux, macOS, WSL) comme sous `cmd.exe` (Windows, Git Bash) ;
seul `kind` doit être dans le `PATH`.

Après un nouveau `docker compose build`, les images portent toujours le tag `latest` : Terraform ne voit pas le
changement. Forcer le rechargement puis redémarrer les pods :

```bash
terraform apply -var-file=envs/dev.tfvars -var local_images_revision=2
kubectl -n smartrecruit rollout restart deployment
```

### Secrets

En dev, Terraform génère les secrets (`random_password`, `random_bytes` pour la clé AES des CV) et les transmet au
chart. Ils sont stockés **en clair dans le fichier d'état** : `state/` est ignoré par git et ne doit jamais être
partagé publiquement. La clé OpenRouter se passe par variable d'environnement :

```bash
export TF_VAR_openrouter_api_key=sk-or-...          # bash
$env:TF_VAR_openrouter_api_key = "sk-or-..."        # PowerShell
terraform apply -var-file=envs/dev.tfvars -var ai_engine=llm
```

### Supprimer l'environnement

```bash
terraform destroy -var-file=envs/dev.tfvars
```

Le cluster kind est supprimé avec toutes ses données (Postgres, CV, métriques, logs).

## Production

`envs/prod.tfvars` cible un cluster existant (`kubeconfig_path`, `kube_context`), utilise les images publiées sur
`ghcr.io` par le pipeline (`.github/workflows/pipeline.yml`), active TLS (ClusterIssuer cert-manager `letsencrypt` à installer) et
**ne crée pas les secrets** : `app-secrets` doit exister avant l'apply (voir `deploy/k8s/README.md`, Sealed Secrets ou
External Secrets), ce qui évite de les écrire dans l'état Terraform.

```bash
terraform workspace new prod        # état séparé : terraform.tfstate.d/prod/
terraform apply -var-file=envs/prod.tfvars -var image_tag=<sha du commit>
terraform workspace select default  # retour au dev
```

## État (state) et backend

- `backend "local"` écrit l'état dans `state/terraform.tfstate` (comme le TP 5) ; chaque workspace a son propre
  fichier (`terraform.tfstate.d/<workspace>/`).
- Le backend local **ne pose pas de verrou** : un seul `apply` à la fois.
- `terraform state list`, `terraform show`, `terraform output` pour inspecter ; `terraform apply -refresh-only`
  pour réaligner l'état sur la réalité.
- En équipe : backend distant avec verrou (`s3` + `use_lockfile`, `azurerm`, Terraform Cloud) ; seul le bloc
  `backend` de `versions.tf` change, puis `terraform init -migrate-state`.

## Notions du cours utilisées

| Notion | Où |
|---|---|
| providers et versions | `versions.tf`, `providers.tf` |
| variables d'entrée + `validation` | `variables.tf` (environment, ports, tag, domaine, e-mail, mot de passe...) |
| locals, expressions `for`, conditions | `main.tf` (`hosts`, `image_pull_policy`, `port_suffix`) |
| `count` | `module.kind_cluster`, `helm_release.kube_prometheus_stack`, `helm_release.elk` |
| `for_each` | `null_resource.kind_load` (une ressource par image) ; blocs `dynamic "set"` |
| modules | `modules/kind-cluster`, `modules/platform-addons`, `modules/smartrecruit` |
| dépendances | implicites (références) et explicites (`depends_on` entre modules) |
| outputs (dont `sensitive`) | `outputs.tf` |
| fonctions | `one`, `merge`, `concat`, `join`, `fileexists`, `file`, `yamlencode`, `abspath`, `pathexpand`, `coalesce` |
| `check` (assertion non bloquante) | `main.tf` : moteur `llm` sans clé OpenRouter |
| backend local, workspaces | `versions.tf`, section ci-dessus |

## Limites connues

- Le provider `helm` ne détecte pas les modifications des templates d'un chart local dont la version
  (`Chart.yaml`) ne change pas : incrémenter `version` dans `Chart.yaml`, ou
  `terraform apply -replace=module.smartrecruit.helm_release.smartrecruit`.
- Le cluster kind et les releases Helm sont créés dans le même `apply` : si le plan échoue faute de cluster
  (provider configuré avec des valeurs inconnues), lancer d'abord `terraform apply -target=module.kind_cluster`.
- Versions des charts figées en variables (`*_chart_version`) : les mettre à jour volontairement après lecture
  des notes de version.
