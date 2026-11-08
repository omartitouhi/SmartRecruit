variable "cluster_name" {
  description = "Nom du cluster kind."
  type        = string
}

variable "node_image" {
  description = "Image du nœud (vide = image par défaut du provider kind)."
  type        = string
  default     = ""
}

variable "http_port" {
  description = "Port hôte publié vers le port 80 du nœud (Ingress HTTP)."
  type        = number
  default     = 80
}

variable "https_port" {
  description = "Port hôte publié vers le port 443 du nœud (Ingress HTTPS)."
  type        = number
  default     = 443
}

variable "kubeconfig_path" {
  description = "Fichier kubeconfig écrit par le provider (vide = ~/.kube/config, fusionné avec l'existant)."
  type        = string
  default     = ""
}
