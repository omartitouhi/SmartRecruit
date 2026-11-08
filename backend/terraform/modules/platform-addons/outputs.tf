output "ingress_class_name" {
  description = "IngressClass créée par ingress-nginx (null s'il n'est pas installé)."
  value       = var.install_ingress_nginx ? var.ingress_class_name : null
}

output "monitoring_namespace" {
  description = "Namespace de kube-prometheus-stack (null si désactivé)."
  value       = one(helm_release.kube_prometheus_stack[*].namespace)
}

output "grafana_release" {
  description = "Nom de la release kube-prometheus-stack (le Secret Grafana s'appelle <release>-grafana)."
  value       = one(helm_release.kube_prometheus_stack[*].name)
}

output "releases" {
  description = "Releases Helm installées par ce module, avec leur version de chart."
  value = merge(
    { for r in helm_release.ingress_nginx : r.name => r.version },
    { for r in helm_release.metrics_server : r.name => r.version },
    { for r in helm_release.kube_prometheus_stack : r.name => r.version },
  )
}
