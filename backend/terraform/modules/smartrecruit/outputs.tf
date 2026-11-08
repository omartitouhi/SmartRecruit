output "namespace" {
  description = "Namespace de l'application."
  value       = kubernetes_namespace.smartrecruit.metadata[0].name
}

output "release" {
  description = "Release Helm de l'application : nom, révision et statut."
  value = {
    name     = helm_release.smartrecruit.name
    revision = helm_release.smartrecruit.metadata[0].revision
    status   = helm_release.smartrecruit.status
  }
}

output "elk_namespace" {
  description = "Namespace de la stack ELK (null si désactivée)."
  value       = one(helm_release.elk[*].namespace)
}

output "loaded_images" {
  description = "Images locales chargées dans kind."
  value       = sort(keys(null_resource.kind_load))
}
