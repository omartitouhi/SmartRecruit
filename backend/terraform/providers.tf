# Connexion au cluster.
#   create_cluster = true  : identifiants fournis par le module kind (cluster créé dans le même apply).
#   create_cluster = false : kubeconfig existant (cluster de production, minikube, Docker Desktop...).
# one(...[*]) renvoie null quand le module kind n'est pas créé (count = 0), sans erreur d'index.

locals {
  kind_endpoint       = one(module.kind_cluster[*].endpoint)
  kind_client_cert    = one(module.kind_cluster[*].client_certificate)
  kind_client_key     = one(module.kind_cluster[*].client_key)
  kind_ca_certificate = one(module.kind_cluster[*].cluster_ca_certificate)

  external_config_path    = var.create_cluster ? null : pathexpand(var.kubeconfig_path)
  external_config_context = var.create_cluster || var.kube_context == "" ? null : var.kube_context
}

provider "kind" {}

provider "kubernetes" {
  host                   = local.kind_endpoint
  client_certificate     = local.kind_client_cert
  client_key             = local.kind_client_key
  cluster_ca_certificate = local.kind_ca_certificate

  config_path    = local.external_config_path
  config_context = local.external_config_context
}

provider "helm" {
  kubernetes {
    host                   = local.kind_endpoint
    client_certificate     = local.kind_client_cert
    client_key             = local.kind_client_key
    cluster_ca_certificate = local.kind_ca_certificate

    config_path    = local.external_config_path
    config_context = local.external_config_context
  }
}
