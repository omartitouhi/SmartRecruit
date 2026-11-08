# Composants communs du cluster, installés depuis les dépôts Helm officiels (versions figées par variables).

locals {
  # Réglages d'ingress-nginx pour kind (cf. https://kind.sigs.k8s.io/docs/user/ingress/) :
  # le contrôleur tourne sur le nœud « ingress-ready » et écoute directement sur ses ports 80/443 (hostPort),
  # que kind publie sur la machine hôte.
  ingress_nginx_kind_settings = {
    "controller.hostPort.enabled"                            = { value = "true", type = "auto" }
    "controller.service.type"                                = { value = "NodePort", type = "string" }
    "controller.nodeSelector.ingress-ready"                  = { value = "true", type = "string" }
    "controller.tolerations[0].key"                          = { value = "node-role.kubernetes.io/control-plane", type = "string" }
    "controller.tolerations[0].operator"                     = { value = "Exists", type = "string" }
    "controller.tolerations[0].effect"                       = { value = "NoSchedule", type = "string" }
    "controller.publishService.enabled"                      = { value = "false", type = "auto" }
    "controller.extraArgs.publish-status-address"            = { value = "localhost", type = "string" }
    "controller.updateStrategy.type"                         = { value = "RollingUpdate", type = "string" }
    "controller.updateStrategy.rollingUpdate.maxSurge"       = { value = "0", type = "auto" }
    "controller.updateStrategy.rollingUpdate.maxUnavailable" = { value = "1", type = "auto" }
  }

  # Réglages valables partout (kind ou cluster managé).
  ingress_nginx_common_settings = {
    "controller.ingressClassResource.name"    = { value = var.ingress_class_name, type = "string" }
    "controller.ingressClassResource.default" = { value = "true", type = "auto" }
    "controller.watchIngressWithoutClass"     = { value = "true", type = "auto" }
    "controller.metrics.enabled"              = { value = "true", type = "auto" }
  }

  # Expression for filtrée par une condition (if) : les réglages kind ne sont ajoutés qu'en mode kind.
  ingress_nginx_settings = merge(
    local.ingress_nginx_common_settings,
    { for name, setting in local.ingress_nginx_kind_settings : name => setting if var.kind_mode },
  )
}

########################################
# ingress-nginx : point d'entrée HTTP(S) du cluster
########################################

resource "helm_release" "ingress_nginx" {
  count = var.install_ingress_nginx ? 1 : 0

  name             = "ingress-nginx"
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  version          = var.ingress_nginx_chart_version
  namespace        = "ingress-nginx"
  create_namespace = true

  # wait : le webhook d'admission doit répondre avant que SmartRecruit crée ses Ingress.
  wait    = true
  timeout = var.timeout_seconds

  dynamic "set" {
    for_each = local.ingress_nginx_settings
    content {
      name  = set.key
      value = set.value.value
      type  = set.value.type
    }
  }
}

########################################
# metrics-server : métriques CPU/mémoire des pods (HPA, kubectl top)
########################################

resource "helm_release" "metrics_server" {
  count = var.install_metrics_server ? 1 : 0

  name       = "metrics-server"
  repository = "https://kubernetes-sigs.github.io/metrics-server/"
  chart      = "metrics-server"
  version    = var.metrics_server_chart_version
  namespace  = "kube-system"

  wait    = true
  timeout = var.timeout_seconds

  # Les kubelets de kind ont des certificats auto-signés.
  dynamic "set" {
    for_each = var.kind_mode ? ["--kubelet-insecure-tls"] : []
    content {
      name  = "args[${set.key}]"
      value = set.value
    }
  }
}

########################################
# kube-prometheus-stack : Prometheus Operator, Prometheus, Alertmanager, Grafana, node-exporter (DaemonSet)
########################################

resource "helm_release" "kube_prometheus_stack" {
  count = var.enable_monitoring ? 1 : 0

  name             = "monitoring"
  repository       = "https://prometheus-community.github.io/helm-charts"
  chart            = "kube-prometheus-stack"
  version          = var.kube_prometheus_stack_chart_version
  namespace        = "monitoring"
  create_namespace = true

  wait    = true
  timeout = var.timeout_seconds

  # Valeurs du dépôt (deploy/monitoring/kube-prometheus-stack-values.yaml), puis surcharges ci-dessous.
  values = fileexists(var.monitoring_values_file) ? [file(var.monitoring_values_file)] : []

  # Prometheus lit les ServiceMonitors / PrometheusRules de tous les namespaces, quels que soient leurs labels.
  set {
    name  = "prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues"
    value = "false"
  }

  set {
    name  = "prometheus.prometheusSpec.ruleSelectorNilUsesHelmValues"
    value = "false"
  }

  set {
    name  = "grafana.ingress.enabled"
    value = var.ingress_enabled ? "true" : "false"
  }

  set {
    name  = "grafana.ingress.ingressClassName"
    value = var.ingress_class_name
    type  = "string"
  }

  set {
    name  = "grafana.ingress.hosts[0]"
    value = var.grafana_host
    type  = "string"
  }

  # Les Ingress de Grafana ont besoin du webhook d'ingress-nginx.
  depends_on = [helm_release.ingress_nginx]
}
