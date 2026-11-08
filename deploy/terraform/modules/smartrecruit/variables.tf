variable "environment" {
  description = "dev ou prod : choisit values-<environment>.yaml des charts."
  type        = string
}

variable "cluster_name" {
  description = "Cluster kind cible du chargement des images locales."
  type        = string
}

variable "charts_dir" {
  description = "Dossier contenant les charts locaux smartrecruit/ et elk/."
  type        = string
}

variable "namespace" {
  description = "Namespace de l'application."
  type        = string
  default     = "smartrecruit"
}

variable "services" {
  description = "Services dont les images sont chargées dans kind (image smartrecruit-<service>:<tag>)."
  type        = list(string)
}

variable "load_local_images" {
  description = "Charge les images Docker locales dans le cluster kind."
  type        = bool
  default     = false
}

variable "local_images_revision" {
  description = "Changer cette valeur force le rechargement des images locales (utile avec le tag latest)."
  type        = string
  default     = "1"
}

variable "extra_local_images" {
  description = "Images tierces à charger aussi dans kind. Vide par défaut : le nœud télécharge lui-même les images publiques (postgres:16), car « kind load » échoue sur les images multi-architectures."
  type        = list(string)
  default     = []
}

variable "image_registry" {
  description = "Registre des images (vide = images locales)."
  type        = string
}

variable "image_tag" {
  description = "Tag des images des services."
  type        = string
}

variable "image_pull_policy" {
  description = "imagePullPolicy des services."
  type        = string
}

variable "create_app_secret" {
  description = "Le chart crée le Secret app-secrets."
  type        = bool
}

variable "existing_secret_name" {
  description = "Secret existant quand create_app_secret = false."
  type        = string
  default     = ""
}

variable "app_secrets" {
  description = "Valeurs du Secret app-secrets (DB_USER, DB_PASSWORD, JWT_SECRET...)."
  type        = map(string)
  sensitive   = true
}

variable "ai_engine" {
  description = "Moteur du service IA (rules ou llm)."
  type        = string
}

variable "openrouter_model" {
  description = "Modèle OpenRouter."
  type        = string
}

variable "ingress_enabled" {
  description = "Crée les Ingress de l'application et de Kibana."
  type        = bool
}

variable "ingress_class_name" {
  description = "IngressClass."
  type        = string
}

variable "ingress_tls_enabled" {
  description = "TLS sur l'Ingress de l'application."
  type        = bool
}

variable "app_host" {
  description = "Nom d'hôte de l'application."
  type        = string
}

variable "enable_monitoring" {
  description = "Active ServiceMonitors, PrometheusRule et dashboard Grafana dans le chart (CRDs de kube-prometheus-stack requises)."
  type        = bool
}

variable "enable_logging" {
  description = "Installe le chart ELK."
  type        = bool
}

variable "kibana_host" {
  description = "Nom d'hôte de Kibana."
  type        = string
}

variable "timeout_seconds" {
  description = "Délai d'attente des releases Helm."
  type        = number
  default     = 1200
}
