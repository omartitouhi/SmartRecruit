# Variables d'entrée. Valeurs par défaut = développement local sur kind.
# Surcharges : envs/dev.tfvars, envs/prod.tfvars, terraform.tfvars, -var "x=y" ou TF_VAR_x.

########################################
# Environnement et cluster
########################################

variable "environment" {
  description = "Environnement cible : choisit values-<environment>.yaml dans les charts."
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment doit valoir \"dev\" ou \"prod\"."
  }
}

variable "create_cluster" {
  description = "true : crée un cluster kind local. false : utilise le cluster du kubeconfig (kubeconfig_path / kube_context)."
  type        = bool
  default     = true
}

variable "cluster_name" {
  description = "Nom du cluster kind (aussi utilisé par `kind load docker-image --name`)."
  type        = string
  default     = "smartrecruit-tf"

  validation {
    condition     = can(regex("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$", var.cluster_name)) && length(var.cluster_name) <= 40
    error_message = "cluster_name : minuscules, chiffres et tirets uniquement (40 caractères max)."
  }
}

variable "kind_node_image" {
  description = "Image du nœud kind (ex. kindest/node:v1.33.1). Vide = image par défaut du provider."
  type        = string
  default     = ""
}

variable "kubeconfig_path" {
  description = "Kubeconfig utilisé quand create_cluster = false."
  type        = string
  default     = "~/.kube/config"
}

variable "kube_context" {
  description = "Contexte du kubeconfig quand create_cluster = false (vide = contexte courant)."
  type        = string
  default     = ""
}

variable "http_port" {
  description = "Port HTTP de la machine hôte publié vers l'Ingress du cluster kind."
  type        = number
  default     = 80

  validation {
    condition     = var.http_port >= 1 && var.http_port <= 65535
    error_message = "http_port doit être compris entre 1 et 65535."
  }
}

variable "https_port" {
  description = "Port HTTPS de la machine hôte publié vers l'Ingress du cluster kind."
  type        = number
  default     = 443

  validation {
    condition     = var.https_port >= 1 && var.https_port <= 65535
    error_message = "https_port doit être compris entre 1 et 65535."
  }
}

########################################
# Images
########################################

variable "image_registry" {
  description = "Registre des images (ex. ghcr.io/omartitouhi/smartrecruit : images <registre>/<service>). Vide = images locales smartrecruit-<service>:<tag> chargées dans kind."
  type        = string
  default     = ""
}

variable "image_tag" {
  description = "Tag des images des services (en production : le SHA du commit publié par le pipeline CI/CD)."
  type        = string
  default     = "latest"

  validation {
    condition     = can(regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$", var.image_tag))
    error_message = "image_tag n'est pas un tag Docker valide."
  }
}

variable "image_pull_policy" {
  description = "imagePullPolicy des services. Vide = automatique (IfNotPresent en local, Always pour latest sur un registre)."
  type        = string
  default     = ""

  validation {
    condition     = contains(["", "Always", "IfNotPresent", "Never"], var.image_pull_policy)
    error_message = "image_pull_policy : \"\", Always, IfNotPresent ou Never."
  }
}

variable "load_local_images" {
  description = "Charge les images Docker locales (construites par `docker compose build`) dans le cluster kind."
  type        = bool
  default     = true
}

variable "local_images_revision" {
  description = "Changer cette valeur (ex. 2, 3...) recharge toutes les images locales dans kind après un `docker compose build`."
  type        = string
  default     = "1"
}

########################################
# Composants de la plateforme
########################################

variable "install_ingress_nginx" {
  description = "Installe le contrôleur ingress-nginx."
  type        = bool
  default     = true
}

variable "install_metrics_server" {
  description = "Installe metrics-server (nécessaire aux HorizontalPodAutoscalers et à `kubectl top`)."
  type        = bool
  default     = true
}

variable "enable_monitoring" {
  description = "Installe kube-prometheus-stack (Prometheus, Grafana, Alertmanager) et active les ServiceMonitors de SmartRecruit."
  type        = bool
  default     = true
}

variable "enable_logging" {
  description = "Installe la stack ELK (Elasticsearch, Logstash, Kibana, Filebeat) dans le namespace logging."
  type        = bool
  default     = true
}

variable "ingress_enabled" {
  description = "Expose l'application (et Grafana / Kibana) par l'Ingress."
  type        = bool
  default     = true
}

variable "ingress_class_name" {
  description = "IngressClass utilisée par toutes les ressources Ingress."
  type        = string
  default     = "nginx"
}

variable "ingress_tls_enabled" {
  description = "Active TLS sur l'Ingress de l'application (production : cert-manager requis)."
  type        = bool
  default     = false
}

variable "base_domain" {
  description = "Nom d'hôte de l'application ; Grafana et Kibana sont servis sur grafana.<base_domain> et kibana.<base_domain>."
  type        = string
  default     = "smartrecruit.local"

  validation {
    condition     = can(regex("^([a-z0-9]([-a-z0-9]*[a-z0-9])?\\.)+[a-z]{2,}$", var.base_domain))
    error_message = "base_domain doit être un nom de domaine valide (ex. smartrecruit.local)."
  }
}

########################################
# Configuration et secrets de l'application
########################################

variable "ai_engine" {
  description = "Moteur du service IA : rules (sans LLM) ou llm (OpenRouter, clé requise)."
  type        = string
  default     = "rules"

  validation {
    condition     = contains(["rules", "llm"], var.ai_engine)
    error_message = "ai_engine doit valoir \"rules\" ou \"llm\"."
  }
}

variable "openrouter_model" {
  description = "Modèle OpenRouter utilisé quand ai_engine = llm."
  type        = string
  default     = "anthropic/claude-haiku-5.5"
}

variable "openrouter_api_key" {
  description = "Clé OpenRouter (vide = endpoints LLM en 503). Passer par TF_VAR_openrouter_api_key."
  type        = string
  default     = ""
  sensitive   = true
}

variable "create_app_secret" {
  description = "true : le chart crée le Secret app-secrets à partir des valeurs ci-dessous. false : Secret existant (existing_secret_name)."
  type        = bool
  default     = true
}

variable "existing_secret_name" {
  description = "Secret créé hors Terraform (Sealed Secrets, External Secrets...) quand create_app_secret = false."
  type        = string
  default     = ""
}

variable "db_user" {
  description = "Utilisateur PostgreSQL."
  type        = string
  default     = "smartrecruit"
}

variable "db_password" {
  description = "Mot de passe PostgreSQL. Vide = généré par Terraform."
  type        = string
  default     = ""
  sensitive   = true
}

variable "jwt_secret" {
  description = "Secret de signature des JWT. Vide = généré par Terraform."
  type        = string
  default     = ""
  sensitive   = true
}

variable "internal_auth_secret" {
  description = "Secret des en-têtes d'identité gateway -> services. Vide = généré par Terraform."
  type        = string
  default     = ""
  sensitive   = true
}

variable "cv_encryption_key" {
  description = "Clé AES des CV (32 octets en base64). Vide = générée par Terraform. La changer rend les CV existants illisibles."
  type        = string
  default     = ""
  sensitive   = true
}

variable "admin_email" {
  description = "E-mail du compte administrateur créé au démarrage d'auth-service."
  type        = string
  default     = "admin@smartrecruit.local"

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.admin_email))
    error_message = "admin_email n'est pas une adresse e-mail valide."
  }
}

variable "admin_password" {
  description = "Mot de passe du compte administrateur (12 caractères minimum). Vide = généré par Terraform."
  type        = string
  default     = ""
  sensitive   = true

  validation {
    condition     = var.admin_password == "" || length(var.admin_password) >= 12
    error_message = "admin_password doit contenir au moins 12 caractères (ou rester vide pour être généré)."
  }
}

########################################
# Versions des charts (figées pour des déploiements reproductibles)
########################################

variable "ingress_nginx_chart_version" {
  description = "Version du chart ingress-nginx."
  type        = string
  default     = "4.12.0"

  validation {
    condition     = can(regex("^\\d+\\.\\d+\\.\\d+$", var.ingress_nginx_chart_version))
    error_message = "Version de chart attendue au format X.Y.Z."
  }
}

variable "metrics_server_chart_version" {
  description = "Version du chart metrics-server."
  type        = string
  default     = "3.12.2"

  validation {
    condition     = can(regex("^\\d+\\.\\d+\\.\\d+$", var.metrics_server_chart_version))
    error_message = "Version de chart attendue au format X.Y.Z."
  }
}

variable "kube_prometheus_stack_chart_version" {
  description = "Version du chart kube-prometheus-stack."
  type        = string
  default     = "66.2.1"

  validation {
    condition     = can(regex("^\\d+\\.\\d+\\.\\d+$", var.kube_prometheus_stack_chart_version))
    error_message = "Version de chart attendue au format X.Y.Z."
  }
}

variable "helm_timeout_seconds" {
  description = "Délai d'attente de chaque release Helm (les services Java démarrent lentement sur un poste de dev)."
  type        = number
  default     = 1800

  validation {
    condition     = var.helm_timeout_seconds >= 60
    error_message = "helm_timeout_seconds doit être d'au moins 60 secondes."
  }
}
