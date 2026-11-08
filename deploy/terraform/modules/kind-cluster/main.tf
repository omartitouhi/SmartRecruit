# Cluster kind à un nœud, prêt pour ingress-nginx (configuration recommandée par la documentation kind) :
#   - label ingress-ready=true sur le nœud (le contrôleur y est épinglé par nodeSelector) ;
#   - ports 80/443 du nœud publiés sur la machine hôte (hostPort du contrôleur).

locals {
  ingress_ready_patch = <<-EOT
    kind: InitConfiguration
    nodeRegistration:
      kubeletExtraArgs:
        node-labels: "ingress-ready=true"
  EOT
}

resource "kind_cluster" "this" {
  name            = var.cluster_name
  node_image      = var.node_image != "" ? var.node_image : null
  kubeconfig_path = var.kubeconfig_path != "" ? pathexpand(var.kubeconfig_path) : null
  wait_for_ready  = true

  kind_config {
    kind        = "Cluster"
    api_version = "kind.x-k8s.io/v1alpha4"

    node {
      role = "control-plane"

      # replace : patch identique (LF) même si le code vient d'une copie de travail Windows (CRLF).
      kubeadm_config_patches = [replace(local.ingress_ready_patch, "\r", "")]

      extra_port_mappings {
        container_port = 80
        host_port      = var.http_port
        protocol       = "TCP"
      }

      extra_port_mappings {
        container_port = 443
        host_port      = var.https_port
        protocol       = "TCP"
      }
    }
  }

  # Ce patch ne s'applique qu'à la création du nœud : une différence de forme (fins de ligne) ne doit jamais
  # détruire le cluster et toutes ses données.
  lifecycle {
    ignore_changes = [kind_config[0].node[0].kubeadm_config_patches]
  }
}
