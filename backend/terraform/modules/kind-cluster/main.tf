# Cluster kind à un nœud, prêt pour ingress-nginx (configuration recommandée par la documentation kind) :
#   - label ingress-ready=true sur le nœud (le contrôleur y est épinglé par nodeSelector) ;
#   - ports 80/443 du nœud publiés sur la machine hôte (hostPort du contrôleur).

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

      kubeadm_config_patches = [
        <<-EOT
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
        EOT
      ]

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
}
