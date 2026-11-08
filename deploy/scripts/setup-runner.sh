#!/usr/bin/env bash
# Installe le runner GitHub Actions auto-hébergé SUR la VM (étape « 4 · Déploiement » du pipeline).
#
#   bash deploy/scripts/setup-runner.sh            (sur la VM, en tant qu'utilisateur d'exploitation)
#   bash deploy/scripts/setup-runner.sh --download (télécharge seulement, sans enregistrement)
#
# Jeton d'enregistrement : GitHub → dépôt → Settings → Actions → Runners → New self-hosted runner (valable 1 h).
# Il est demandé en saisie masquée et n'est écrit nulle part. Le runner tourne ensuite comme service systemd
# et contacte GitHub en sortie (HTTPS) : aucun port entrant à ouvrir.
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/omartitouhi/SmartRecruit}"
RUNNER_DIR="${RUNNER_DIR:-/opt/actions-runner}"
RUNNER_NAME="${RUNNER_NAME:-smartrecruit-vm}"
RUNNER_LABELS="${RUNNER_LABELS:-smartrecruit-vm}"

case "$(uname -m)" in
  x86_64) ARCH=x64 ;;
  aarch64|arm64) ARCH=arm64 ;;
  *) echo "Architecture non prise en charge : $(uname -m)" >&2; exit 1 ;;
esac

if [ ! -x "$RUNNER_DIR/config.sh" ]; then
  RELEASE="$(curl -fsS https://api.github.com/repos/actions/runner/releases/latest)"
  VERSION="$(printf '%s\n' "$RELEASE" | grep -m1 '"tag_name"' | sed -E 's/.*"v([^"]+)".*/\1/')"
  echo "==> Téléchargement du runner $VERSION ($ARCH) dans $RUNNER_DIR"
  sudo mkdir -p "$RUNNER_DIR"
  sudo chown "$USER:$USER" "$RUNNER_DIR"
  curl -fsSL "https://github.com/actions/runner/releases/download/v$VERSION/actions-runner-linux-$ARCH-$VERSION.tar.gz" \
    | tar xz -C "$RUNNER_DIR"
  sudo "$RUNNER_DIR/bin/installdependencies.sh" >/dev/null
fi
[ "${1:-}" = "--download" ] && { echo "Runner téléchargé ; relancer sans --download pour l'enregistrer."; exit 0; }

cd "$RUNNER_DIR"
if [ -f .runner ]; then
  echo "Runner déjà enregistré ($(grep -o '"agentName": *"[^"]*"' .runner | cut -d'"' -f4))."
else
  echo "Collez le jeton d'enregistrement (Settings → Actions → Runners → New self-hosted runner)."
  read -rsp "Jeton (masqué) : " TOKEN; echo
  [ -n "$TOKEN" ] || { echo "Jeton vide." >&2; exit 1; }
  ./config.sh --unattended --replace --url "$REPO_URL" --token "$TOKEN" \
    --name "$RUNNER_NAME" --labels "$RUNNER_LABELS" --work _work
  unset TOKEN
fi

# Service systemd : démarre avec la VM, relancé en cas d'arrêt.
if ! systemctl list-units --type=service --all | grep -q "actions.runner"; then
  sudo ./svc.sh install "$USER"
fi
sudo ./svc.sh start
sudo ./svc.sh status | head -5
echo "==> Runner « $RUNNER_NAME » prêt (labels : self-hosted, linux, $RUNNER_LABELS)."
