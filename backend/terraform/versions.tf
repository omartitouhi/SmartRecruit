# Bloc « terraform » : métadonnées du projet (version de Terraform, providers, backend).
terraform {
  required_version = ">= 1.5"

  required_providers {
    # Cluster Kubernetes local dans Docker (1 conteneur = 1 nœud).
    kind = {
      source  = "tehcyx/kind"
      version = "~> 0.9"
    }
    # Installe les charts Helm (ingress-nginx, kube-prometheus-stack, smartrecruit, elk).
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.17"
    }
    # Ressources Kubernetes natives (namespace).
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.35"
    }
    # Commandes locales (chargement des images Docker dans kind).
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
    }
    # Secrets générés (mots de passe, clé de chiffrement des CV).
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # État partagé sur disque, comme le TP 5 : un seul fichier, hors du dossier de travail.
  # Pas de verrou avec le backend local : ne jamais lancer deux `apply` en même temps.
  # Étape suivante en équipe : backend "s3" (+ verrou) ou Terraform Cloud.
  backend "local" {
    path = "state/terraform.tfstate"
  }
}
