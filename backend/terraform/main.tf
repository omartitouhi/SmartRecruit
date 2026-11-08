# Module racine : cluster (optionnel) -> composants de la plateforme -> SmartRecruit (+ ELK).

locals {
  # Les 8 images construites par les dépôts SmartRecruit.
  services = [
    "auth-service",
    "user-service",
    "job-service",
    "application-service",
    "dashboard-service",
    "api-gateway",
    "ai-service",
    "frontend",
  ]

  # Noms d'hôtes calculés avec une expression for : app -> smartrecruit.local, grafana -> grafana.smartrecruit.local...
  host_prefixes = {
    app     = ""
    grafana = "grafana"
    kibana  = "kibana"
  }
  hosts = {
    for name, prefix in local.host_prefixes :
    name => prefix == "" ? var.base_domain : "${prefix}.${var.base_domain}"
  }

  charts_dir             = abspath("${path.root}/../helm")
  monitoring_values_file = abspath("${path.root}/../monitoring/kube-prometheus-stack-values.yaml")

  # Images locales uniquement sur un cluster kind créé ici et sans registre.
  load_local_images = var.create_cluster && var.load_local_images && var.image_registry == ""

  # Condition : politique de téléchargement automatique si non imposée.
  image_pull_policy = (
    var.image_pull_policy != "" ? var.image_pull_policy :
    var.image_registry == "" ? "IfNotPresent" :
    var.image_tag == "latest" ? "Always" : "IfNotPresent"
  )

  scheme      = var.ingress_tls_enabled ? "https" : "http"
  port_suffix = !var.create_cluster ? "" : var.ingress_tls_enabled ? (var.https_port == 443 ? "" : ":${var.https_port}") : (var.http_port == 80 ? "" : ":${var.http_port}")
}

########################################
# Secrets générés quand ils ne sont pas fournis
########################################

resource "random_password" "db" {
  length  = 24
  special = false
}

resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "random_password" "internal_auth" {
  length  = 64
  special = false
}

resource "random_password" "admin" {
  length           = 20
  min_upper        = 2
  min_lower        = 2
  min_numeric      = 2
  min_special      = 2
  override_special = "!@#%-_=+"
}

# Clé AES-256 : 32 octets aléatoires, encodés en base64 (même format que `openssl rand -base64 32`).
resource "random_bytes" "cv_encryption_key" {
  length = 32
}

locals {
  app_secrets = {
    DB_USER              = var.db_user
    DB_PASSWORD          = var.db_password != "" ? var.db_password : random_password.db.result
    JWT_SECRET           = var.jwt_secret != "" ? var.jwt_secret : random_password.jwt.result
    INTERNAL_AUTH_SECRET = var.internal_auth_secret != "" ? var.internal_auth_secret : random_password.internal_auth.result
    CV_ENCRYPTION_KEY    = var.cv_encryption_key != "" ? var.cv_encryption_key : random_bytes.cv_encryption_key.base64
    ADMIN_EMAIL          = var.admin_email
    ADMIN_PASSWORD       = var.admin_password != "" ? var.admin_password : random_password.admin.result
    OPENROUTER_API_KEY   = var.openrouter_api_key
  }
}

# Vérification non bloquante (Terraform >= 1.5) : avertit si le moteur LLM est choisi sans clé.
check "openrouter_key_for_llm" {
  assert {
    condition     = var.ai_engine != "llm" || nonsensitive(var.openrouter_api_key != "")
    error_message = "ai_engine = \"llm\" sans openrouter_api_key : les endpoints IA répondront 503."
  }
}

########################################
# 1. Cluster kind (seulement si create_cluster = true)
########################################

module "kind_cluster" {
  source = "./modules/kind-cluster"
  count  = var.create_cluster ? 1 : 0

  cluster_name = var.cluster_name
  node_image   = var.kind_node_image
  http_port    = var.http_port
  https_port   = var.https_port
}

########################################
# 2. Composants de la plateforme : ingress-nginx, metrics-server, kube-prometheus-stack
########################################

module "platform_addons" {
  source = "./modules/platform-addons"

  kind_mode              = var.create_cluster
  install_ingress_nginx  = var.install_ingress_nginx
  install_metrics_server = var.install_metrics_server
  enable_monitoring      = var.enable_monitoring

  ingress_nginx_chart_version         = var.ingress_nginx_chart_version
  metrics_server_chart_version        = var.metrics_server_chart_version
  kube_prometheus_stack_chart_version = var.kube_prometheus_stack_chart_version
  monitoring_values_file              = local.monitoring_values_file

  ingress_enabled    = var.ingress_enabled
  ingress_class_name = var.ingress_class_name
  grafana_host       = local.hosts.grafana
  timeout_seconds    = var.helm_timeout_seconds

  depends_on = [module.kind_cluster]
}

########################################
# 3. SmartRecruit (+ ELK)
########################################

module "smartrecruit" {
  source = "./modules/smartrecruit"

  environment  = var.environment
  cluster_name = var.cluster_name
  charts_dir   = local.charts_dir
  services     = local.services

  load_local_images     = local.load_local_images
  local_images_revision = var.local_images_revision
  image_registry        = var.image_registry
  image_tag             = var.image_tag
  image_pull_policy     = local.image_pull_policy

  create_app_secret    = var.create_app_secret
  existing_secret_name = var.existing_secret_name
  app_secrets          = local.app_secrets

  ai_engine        = var.ai_engine
  openrouter_model = var.openrouter_model

  ingress_enabled     = var.ingress_enabled
  ingress_class_name  = var.ingress_class_name
  ingress_tls_enabled = var.ingress_tls_enabled
  app_host            = local.hosts.app

  enable_monitoring = var.enable_monitoring
  enable_logging    = var.enable_logging
  kibana_host       = local.hosts.kibana

  timeout_seconds = var.helm_timeout_seconds

  # L'Ingress a besoin du webhook d'ingress-nginx ; les ServiceMonitors ont besoin des CRDs de kube-prometheus-stack.
  depends_on = [module.kind_cluster, module.platform_addons]
}
