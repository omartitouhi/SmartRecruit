# Variables de sortie : affichées après `terraform apply`, relues avec `terraform output [-raw nom]`.

output "environment" {
  description = "Environnement déployé."
  value       = var.environment
}

output "urls" {
  description = "Adresses des interfaces (ajouter les noms d'hôtes au fichier hosts en local, voir hosts_file_line)."
  value = {
    application = var.ingress_enabled ? "${local.scheme}://${local.hosts.app}${local.port_suffix}" : null
    api         = var.ingress_enabled ? "${local.scheme}://${local.hosts.app}${local.port_suffix}/api" : null
    grafana     = var.ingress_enabled && var.enable_monitoring ? "http://${local.hosts.grafana}${var.create_cluster && var.http_port != 80 ? ":${var.http_port}" : ""}" : null
    kibana      = var.ingress_enabled && var.enable_logging ? "http://${local.hosts.kibana}${var.create_cluster && var.http_port != 80 ? ":${var.http_port}" : ""}" : null
  }
}

output "hosts_file_line" {
  description = "Ligne à ajouter à /etc/hosts (Linux/macOS) ou C:\\Windows\\System32\\drivers\\etc\\hosts (Windows)."
  value = join(" ", concat(
    ["127.0.0.1", local.hosts.app],
    var.enable_monitoring ? [local.hosts.grafana] : [],
    var.enable_logging ? [local.hosts.kibana] : [],
  ))
}

output "kubeconfig_path" {
  description = "Kubeconfig à utiliser avec kubectl."
  value       = var.create_cluster ? one(module.kind_cluster[*].kubeconfig_path) : pathexpand(var.kubeconfig_path)
}

output "kube_context" {
  description = "Contexte kubectl du cluster."
  value       = var.create_cluster ? one(module.kind_cluster[*].kube_context) : var.kube_context
}

output "kubectl_get_pods" {
  description = "Commande pour suivre le démarrage des pods."
  value       = "kubectl -n ${module.smartrecruit.namespace} get pods -o wide"
}

output "grafana_admin_password_command" {
  description = "Commande qui affiche le mot de passe de l'utilisateur admin de Grafana."
  value       = var.enable_monitoring ? "kubectl -n monitoring get secret ${coalesce(module.platform_addons.grafana_release, "monitoring")}-grafana -o jsonpath=\"{.data.admin-password}\" | base64 --decode" : null
}

output "prometheus_port_forward_command" {
  description = "Accès à l'interface Prometheus (http://localhost:9090)."
  value       = var.enable_monitoring ? "kubectl -n monitoring port-forward svc/${coalesce(module.platform_addons.grafana_release, "monitoring")}-kube-prometheus-prometheus 9090" : null
}

output "helm_release" {
  description = "Release Helm de SmartRecruit (nom, révision, statut)."
  value       = module.smartrecruit.release
}

output "platform_releases" {
  description = "Charts de la plateforme installés et leur version."
  value       = module.platform_addons.releases
}

output "loaded_images" {
  description = "Images locales chargées dans kind."
  value       = module.smartrecruit.loaded_images
}

output "admin_email" {
  description = "Compte administrateur de SmartRecruit."
  value       = var.admin_email
}

output "admin_password" {
  description = "Mot de passe administrateur (terraform output -raw admin_password)."
  value       = local.app_secrets.ADMIN_PASSWORD
  sensitive   = true
}

output "db_password" {
  description = "Mot de passe PostgreSQL (terraform output -raw db_password)."
  value       = local.app_secrets.DB_PASSWORD
  sensitive   = true
}
