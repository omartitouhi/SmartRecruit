output "name" {
  description = "Nom du cluster."
  value       = kind_cluster.this.name
}

output "endpoint" {
  description = "URL de l'API Kubernetes (127.0.0.1:<port aléatoire>)."
  value       = kind_cluster.this.endpoint
}

output "client_certificate" {
  description = "Certificat client (PEM) de l'administrateur du cluster."
  value       = kind_cluster.this.client_certificate
  sensitive   = true
}

output "client_key" {
  description = "Clé privée client (PEM)."
  value       = kind_cluster.this.client_key
  sensitive   = true
}

output "cluster_ca_certificate" {
  description = "Certificat de l'autorité du cluster (PEM)."
  value       = kind_cluster.this.cluster_ca_certificate
  sensitive   = true
}

output "kubeconfig_path" {
  description = "Fichier kubeconfig mis à jour par kind (contexte kind-<nom>)."
  value       = kind_cluster.this.kubeconfig_path
}

output "kube_context" {
  description = "Contexte kubectl du cluster."
  value       = "kind-${kind_cluster.this.name}"
}
