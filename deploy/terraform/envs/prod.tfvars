# Production : cluster existant (kubeconfig), images publiées sur ghcr.io par le pipeline CI/CD, TLS par cert-manager.
#   terraform apply -var-file=envs/prod.tfvars -var image_tag=<sha du commit>
# État séparé du dev grâce aux workspaces : terraform workspace new prod (puis terraform workspace select prod).
environment    = "prod"
create_cluster = false

kubeconfig_path = "~/.kube/config"
kube_context    = "smartrecruit-prod"

image_registry    = "ghcr.io/omartitouhi"
image_tag         = "latest" # à remplacer par le SHA publié (images immuables)
load_local_images = false

# Sur un cluster managé, ingress-nginx crée un Service LoadBalancer ; metrics-server est souvent déjà présent.
install_ingress_nginx  = true
install_metrics_server = false

enable_monitoring = true
enable_logging    = true

ingress_enabled     = true
ingress_tls_enabled = true # ClusterIssuer cert-manager "letsencrypt" requis
base_domain         = "smartrecruit.example.com"

ai_engine = "llm"

# Les secrets de production viennent de l'extérieur (Sealed Secrets / External Secrets) :
# Terraform ne les écrit alors ni dans l'état ni dans le chart.
create_app_secret    = false
existing_secret_name = "app-secrets"

admin_email = "admin@smartrecruit.example.com"
