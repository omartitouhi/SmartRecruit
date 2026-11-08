#!/usr/bin/env bash
# S'exécute SUR la VM Ubuntu : installe Ansible, extrait le code et lance deploy/ansible/site.yml en local.
# Appelé par deploy/scripts/deploy-vm.sh (depuis un poste, via SSH) et par l'étape « 4 · Déploiement » du pipeline
# (runner GitHub Actions installé sur la VM).
#
# Variables (toutes optionnelles) :
#   ARCHIVE      archive du code (git archive)            défaut /tmp/smartrecruit.tar.gz
#   REMOTE_DIR   dossier d'installation                    défaut /opt/smartrecruit
#   METHOD       terraform | compose | kind-script         défaut terraform
#   DOMAIN       domaine de base                           défaut <ip publique>.nip.io
#   MONITORING   true | false                              défaut : true si RAM >= 8 Go
#   LOGGING      true | false                              défaut : true si RAM >= 14 Go
set -euo pipefail

ARCHIVE="${ARCHIVE:-/tmp/smartrecruit.tar.gz}"
REMOTE_DIR="${REMOTE_DIR:-/opt/smartrecruit}"
METHOD="${METHOD:-terraform}"
MEM_GB="$(awk '/MemTotal/ {printf "%d", $2/1024/1024 + 0.5}' /proc/meminfo)"
if [ -z "${DOMAIN:-}" ]; then
  DOMAIN="$(curl -fsS --max-time 10 https://api.ipify.org).nip.io"
fi
[ -n "${MONITORING:-}" ] || { [ "$MEM_GB" -ge 8 ] && MONITORING=true || MONITORING=false; }
[ -n "${LOGGING:-}" ] || { [ "$MEM_GB" -ge 14 ] && LOGGING=true || LOGGING=false; }
[ "$METHOD" = "terraform" ] || { MONITORING=false; LOGGING=false; }
[ -f "$ARCHIVE" ] || { echo "Archive introuvable : $ARCHIVE" >&2; exit 1; }

# Un seul déploiement à la fois (poste et pipeline) : le verrou est gardé jusqu'à la fin du playbook.
exec 9>/tmp/smartrecruit-deploy.lock
if ! flock -n 9; then
  echo "Un déploiement est déjà en cours, attente de sa fin (1 h maximum)..."
  flock -w 3600 9 || { echo "Toujours en cours après 1 h : abandon." >&2; exit 1; }
fi

echo "==> VM : RAM ${MEM_GB} Go ; méthode ${METHOD} ; monitoring=${MONITORING} ; ELK=${LOGGING} ; domaine ${DOMAIN}"
export DEBIAN_FRONTEND=noninteractive
sudo apt-get update -qq
sudo apt-get install -y -qq python3-venv git openssl curl >/dev/null
# Ansible récent dans un environnement Python (le paquet apt d'Ubuntu 22.04 est trop ancien pour les collections
# community.docker / community.general utilisées) ; le paquet « ansible » inclut ces collections.
ANSIBLE_VENV="$HOME/.ansible-venv"
[ -x "$ANSIBLE_VENV/bin/ansible-playbook" ] || { python3 -m venv "$ANSIBLE_VENV" && "$ANSIBLE_VENV/bin/pip" install -q "ansible>=10,<12"; }

# Swap de secours : les VM Azure n'en ont pas, et les JVM dépassent vite la RAM au démarrage.
if [ "$MEM_GB" -lt 16 ] && ! swapon --show | grep -q .; then
  sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile && sudo mkswap -q /swapfile && sudo swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

sudo mkdir -p "$REMOTE_DIR"
sudo tar xzf "$ARCHIVE" -C "$REMOTE_DIR"
sudo chown -R "$USER:$USER" "$REMOTE_DIR"
rm -f "$ARCHIVE"

# Secrets propres à cette VM (le site est public) : générés une fois, lisibles par l'utilisateur seulement.
VAULT="$REMOTE_DIR/deploy/ansible/inventory/group_vars/all/vault.yml"
if [ ! -f "$VAULT" ]; then
  (
    umask 077
    {
      echo "vault_admin_password: \"Admin-$(openssl rand -hex 8)\""
      echo "vault_db_password: \"$(openssl rand -hex 16)\""
      echo "vault_openrouter_api_key: \"\""
    } > "$VAULT"
  )
fi

cd "$REMOTE_DIR/deploy/ansible"
rm -f "$REMOTE_DIR/deploy.status"
# nohup : le playbook continue si la connexion SSH ou le job coupe ; journal dans deploy.log.
nohup bash -c "$ANSIBLE_VENV/bin/ansible-playbook site.yml --limit smartrecruit_local \
  -e smartrecruit_user=$USER -e smartrecruit_source=local \
  -e smartrecruit_deploy_method=$METHOD -e smartrecruit_base_domain=$DOMAIN \
  -e smartrecruit_enable_monitoring=$MONITORING -e smartrecruit_enable_logging=$LOGGING; \
  echo \$? > $REMOTE_DIR/deploy.status" > "$REMOTE_DIR/deploy.log" 2>&1 &
PID=$!
tail -n +1 -f "$REMOTE_DIR/deploy.log" --pid="$PID" || true
STATUS="$(cat "$REMOTE_DIR/deploy.status" 2>/dev/null || echo 1)"
if [ "$STATUS" = "0" ]; then
  echo
  echo "==> Déployé : http://$DOMAIN/"
  [ "$MONITORING" = true ] && echo "    Grafana : http://grafana.$DOMAIN/"
  [ "$LOGGING" = true ] && echo "    Kibana  : http://kibana.$DOMAIN/"
  echo "    Compte administrateur : $(grep '^ADMIN_EMAIL=' "$REMOTE_DIR/.env" | cut -d= -f2) (mot de passe : vault_admin_password dans $VAULT)"
fi
exit "$STATUS"
