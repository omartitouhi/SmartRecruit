#!/usr/bin/env bash
# Cluster Kubernetes local (kind) pour SmartRecruit.
#   k8s/scripts/dev.sh up              crée le cluster, construit/charge les images et déploie
#   k8s/scripts/dev.sh load            recharge les images locales puis redémarre les services
#   k8s/scripts/dev.sh status          état des pods
#   k8s/scripts/dev.sh logs <service>  logs d'un service (ex. user-service)
#   k8s/scripts/dev.sh down            supprime le cluster
# Prérequis : Docker, kind, kubectl. Arrêter `docker compose` avant (mêmes ports : 8080, 8090, 8001).
set -euo pipefail

K8S_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_DIR="$(cd "$K8S_DIR/.." && pwd)"
CLUSTER=smartrecruit
NS=smartrecruit
SERVICES=(auth-service user-service job-service application-service dashboard-service api-gateway ai-service frontend)

ensure_secrets() {
  local file="$K8S_DIR/overlays/dev/secrets.env" key
  [ -f "$file" ] && return
  cp "$file.example" "$file"
  # Réutilise la clé OpenRouter déjà configurée pour docker compose.
  if [ -f "$BACKEND_DIR/.env" ]; then
    key="$(grep '^OPENROUTER_API_KEY=' "$BACKEND_DIR/.env" | cut -d= -f2- || true)"
    if [ -n "${key:-}" ]; then
      sed -i "s|^OPENROUTER_API_KEY=.*|OPENROUTER_API_KEY=$key|" "$file"
    fi
  fi
}

ensure_images() {
  local missing=()
  for s in "${SERVICES[@]}"; do
    docker image inspect "smartrecruit-$s:latest" >/dev/null 2>&1 || missing+=("$s")
  done
  if [ "${#missing[@]}" -gt 0 ]; then
    echo "Construction des images manquantes : ${missing[*]}"
    (cd "$BACKEND_DIR" && docker compose build "${missing[@]}")
  fi
}

load_images() {
  for s in "${SERVICES[@]}"; do
    kind load docker-image "smartrecruit-$s:latest" --name "$CLUSTER"
  done
  kind load docker-image postgres:16 --name "$CLUSTER"
}

case "${1:-}" in
  up)
    kind get clusters 2>/dev/null | grep -qx "$CLUSTER" || kind create cluster --config "$K8S_DIR/kind-config.yaml"
    kubectl config use-context "kind-$CLUSTER" >/dev/null
    ensure_secrets
    ensure_images
    load_images
    kubectl apply -k "$K8S_DIR/overlays/dev"
    kubectl -n "$NS" rollout status statefulset/postgres --timeout=300s
    for s in "${SERVICES[@]}"; do kubectl -n "$NS" rollout status "deployment/$s" --timeout=600s; done
    echo "Prêt : http://localhost:8090 (interface), http://localhost:8080 (API), http://localhost:8001/docs (IA)"
    ;;
  load)
    load_images
    for s in "${SERVICES[@]}"; do kubectl -n "$NS" rollout restart "deployment/$s"; done
    ;;
  status)
    kubectl -n "$NS" get pods -o wide
    ;;
  logs)
    kubectl -n "$NS" logs -f --tail=100 "deployment/${2:?service attendu}"
    ;;
  down)
    kind delete cluster --name "$CLUSTER"
    ;;
  *)
    sed -n '2,8p' "$0"
    exit 1
    ;;
esac
