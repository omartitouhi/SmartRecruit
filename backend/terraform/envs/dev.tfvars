# Développement local : cluster kind créé par Terraform, images construites par `docker compose build`.
#   terraform apply -var-file=envs/dev.tfvars
environment    = "dev"
create_cluster = true
cluster_name   = "smartrecruit-tf"

# Ports de la machine hôte publiés vers l'Ingress (mettre 8081/8443... si 80/443 sont pris).
http_port  = 80
https_port = 443

image_registry    = ""
image_tag         = "latest"
load_local_images = true

enable_monitoring = true
enable_logging    = true

ingress_enabled     = true
ingress_tls_enabled = false
base_domain         = "smartrecruit.local"

ai_engine = "rules"

# Secrets : laissés vides, Terraform les génère (terraform output -raw admin_password).
# La clé OpenRouter se passe par variable d'environnement : export TF_VAR_openrouter_api_key=...
admin_email = "admin@smartrecruit.local"
