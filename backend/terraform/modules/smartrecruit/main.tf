# Déploiement de SmartRecruit (chart local helm/smartrecruit) et de la stack de logs (chart local helm/elk).

locals {
  # Liste construite avec une expression for, convertie en ensemble pour for_each.
  local_images = var.load_local_images ? toset(concat(
    [for s in var.services : "smartrecruit-${s}:${var.image_tag}"],
    var.extra_local_images,
  )) : toset([])

  app_chart       = "${var.charts_dir}/smartrecruit"
  app_values_file = "${local.app_chart}/values-${var.environment}.yaml"
  elk_chart       = "${var.charts_dir}/elk"
  elk_values_file = "${local.elk_chart}/values-${var.environment}.yaml"
}

########################################
# Images locales -> nœuds kind (une ressource par image)
########################################

# Prérequis : les images existent dans le Docker local (`docker compose build` dans smartrecruit-backend,
# qui construit aussi le service IA et le frontend). La commande n'utilise aucune syntaxe propre à un shell :
# elle passe par /bin/sh sous Linux, macOS, WSL, et par cmd.exe sous Windows (kind doit être dans le PATH).
resource "null_resource" "kind_load" {
  for_each = local.local_images

  # Rejoué si l'image, le cluster ou la révision change.
  triggers = {
    image    = each.value
    cluster  = var.cluster_name
    revision = var.local_images_revision
  }

  provisioner "local-exec" {
    command = "kind load docker-image ${each.value} --name ${var.cluster_name}"
  }
}

########################################
# Namespace de l'application
########################################

resource "kubernetes_namespace" "smartrecruit" {
  metadata {
    name = var.namespace
    labels = {
      "app.kubernetes.io/part-of"    = "smartrecruit"
      "app.kubernetes.io/managed-by" = "terraform"
      environment                    = var.environment
    }
  }
}

########################################
# Release Helm de l'application
########################################

resource "helm_release" "smartrecruit" {
  name             = "smartrecruit"
  chart            = local.app_chart
  namespace        = kubernetes_namespace.smartrecruit.metadata[0].name
  create_namespace = false

  wait            = true
  timeout         = var.timeout_seconds
  cleanup_on_fail = true

  # 1. values-<env>.yaml du chart ; 2. secrets, encodés en YAML plutôt qu'en `set_sensitive` :
  #    `set` interprète les virgules et les backslashs, fréquents dans des secrets générés.
  values = concat(
    fileexists(local.app_values_file) ? [file(local.app_values_file)] : [],
    [yamlencode({
      secrets = {
        values = var.app_secrets
      }
    })],
  )

  set {
    name  = "images.registry"
    value = var.image_registry
    type  = "string"
  }

  set {
    name  = "images.tag"
    value = var.image_tag
    type  = "string"
  }

  set {
    name  = "images.pullPolicy"
    value = var.image_pull_policy
    type  = "string"
  }

  set {
    name  = "secrets.create"
    value = var.create_app_secret ? "true" : "false"
  }

  set {
    name  = "secrets.existingSecret"
    value = var.existing_secret_name
    type  = "string"
  }

  set {
    name  = "config.AI_ENGINE"
    value = var.ai_engine
    type  = "string"
  }

  set {
    name  = "config.OPENROUTER_MODEL"
    value = var.openrouter_model
    type  = "string"
  }

  set {
    name  = "ingress.enabled"
    value = var.ingress_enabled ? "true" : "false"
  }

  set {
    name  = "ingress.host"
    value = var.app_host
    type  = "string"
  }

  set {
    name  = "ingress.className"
    value = var.ingress_class_name
    type  = "string"
  }

  set {
    name  = "ingress.tls.enabled"
    value = var.ingress_tls_enabled ? "true" : "false"
  }

  set {
    name  = "monitoring.enabled"
    value = var.enable_monitoring ? "true" : "false"
  }

  # Les pods ne démarrent qu'une fois les images présentes sur le nœud.
  depends_on = [null_resource.kind_load]
}

########################################
# Release Helm de la stack ELK
########################################

resource "helm_release" "elk" {
  count = var.enable_logging ? 1 : 0

  name             = "elk"
  chart            = local.elk_chart
  namespace        = "logging"
  create_namespace = true

  wait            = true
  timeout         = var.timeout_seconds
  cleanup_on_fail = true

  values = fileexists(local.elk_values_file) ? [file(local.elk_values_file)] : []

  set {
    name  = "kibana.ingress.enabled"
    value = var.ingress_enabled ? "true" : "false"
  }

  set {
    name  = "kibana.ingress.host"
    value = var.kibana_host
    type  = "string"
  }

  set {
    name  = "kibana.ingress.className"
    value = var.ingress_class_name
    type  = "string"
  }
}
