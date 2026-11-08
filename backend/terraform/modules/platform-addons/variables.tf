variable "kind_mode" {
  description = "true : réglages propres à kind (hostPort, nœud ingress-ready, kubelet sans TLS vérifié)."
  type        = bool
}

variable "install_ingress_nginx" {
  description = "Installe ingress-nginx."
  type        = bool
  default     = true
}

variable "install_metrics_server" {
  description = "Installe metrics-server."
  type        = bool
  default     = true
}

variable "enable_monitoring" {
  description = "Installe kube-prometheus-stack."
  type        = bool
  default     = true
}

variable "ingress_nginx_chart_version" {
  description = "Version du chart ingress-nginx."
  type        = string
}

variable "metrics_server_chart_version" {
  description = "Version du chart metrics-server."
  type        = string
}

variable "kube_prometheus_stack_chart_version" {
  description = "Version du chart kube-prometheus-stack."
  type        = string
}

variable "monitoring_values_file" {
  description = "Fichier de valeurs de kube-prometheus-stack (ignoré s'il n'existe pas)."
  type        = string
}

variable "ingress_enabled" {
  description = "Expose Grafana par l'Ingress."
  type        = bool
  default     = true
}

variable "ingress_class_name" {
  description = "IngressClass de Grafana (et classe créée par ingress-nginx)."
  type        = string
  default     = "nginx"
}

variable "grafana_host" {
  description = "Nom d'hôte de Grafana."
  type        = string
}

variable "timeout_seconds" {
  description = "Délai d'attente de chaque release Helm."
  type        = number
  default     = 900
}
