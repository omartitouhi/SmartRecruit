#!/usr/bin/env bash
# Déploie SmartRecruit sur une VM Ubuntu (Azure, AWS, serveur...) depuis ce poste (Linux, macOS, WSL ou Git Bash).
#
#   deploy/scripts/deploy-vm.sh <utilisateur>@<ip-publique> [options]
#
# Options :
#   -i <clé>               clé SSH privée (ex. ~/.ssh/smartrecruit_azure.pem)
#   --method <m>           terraform (défaut : kind + Helm + Ingress), compose (docker compose) ou kind-script
#   --domain <domaine>     domaine de base (défaut : <ip>.nip.io, aucun DNS à configurer)
#   --monitoring/--no-monitoring, --logging/--no-logging
#                          Prometheus/Grafana et ELK ; par défaut selon la RAM de la VM (>= 8 Go et >= 14 Go)
#   --status               affiche seulement l'état du déploiement (pods, adresses)
#
# Étapes : vérifie la VM (RAM, disque), copie le code commité (git archive, aucun jeton GitHub nécessaire)
# et deploy/scripts/vm-install.sh, qui installe Ansible sur la VM et y lance deploy/ansible/site.yml en local.
# Le déploiement continue sur la VM même si la connexion SSH coupe ; journal : /opt/smartrecruit/deploy.log.
# Prérequis côté Azure : ports 22 (votre IP) et 80 (tout le monde) ouverts dans le groupe de sécurité réseau (NSG).
set -euo pipefail

usage() { sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }

TARGET="" KEY="" METHOD="terraform" DOMAIN="" MONITORING="" LOGGING="" STATUS_ONLY=false
while [ $# -gt 0 ]; do
  case "$1" in
    -i) KEY="$2"; shift 2 ;;
    --method) METHOD="$2"; shift 2 ;;
    --domain) DOMAIN="$2"; shift 2 ;;
    --monitoring) MONITORING=true; shift ;;
    --no-monitoring) MONITORING=false; shift ;;
    --logging) LOGGING=true; shift ;;
    --no-logging) LOGGING=false; shift ;;
    --status) STATUS_ONLY=true; shift ;;
    -h|--help) usage ;;
    -*) echo "Option inconnue : $1" >&2; usage 1 ;;
    *) TARGET="$1"; shift ;;
  esac
done
[ -n "$TARGET" ] || usage 1
case "$METHOD" in terraform|compose|kind-script) ;; *) echo "--method : terraform, compose ou kind-script" >&2; exit 1 ;; esac

HOST="${TARGET#*@}"
if [ -z "$DOMAIN" ]; then
  if [[ "$HOST" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then DOMAIN="$HOST.nip.io"
  else echo "La cible n'est pas une adresse IP : préciser --domain." >&2; exit 1; fi
fi

SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ServerAliveInterval=30 -o ServerAliveCountMax=6)
[ -n "$KEY" ] && SSH_OPTS+=(-i "$KEY")
remote() { ssh "${SSH_OPTS[@]}" "$TARGET" "$@"; }

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REMOTE_DIR=/opt/smartrecruit

if $STATUS_ONLY; then
  remote "kubectl get pods -A 2>/dev/null || docker ps; tail -n 5 $REMOTE_DIR/deploy.log 2>/dev/null || true"
  exit 0
fi

echo "==> Vérification de la VM $TARGET"
read -r MEM_GB CPUS DISK_GB OS_ID < <(remote bash -s <<'CHECK'
mem=$(awk '/MemTotal/ {printf "%d", $2/1024/1024 + 0.5}' /proc/meminfo)
disk=$(df -BG --output=avail / | tail -1 | tr -dc 0-9)
. /etc/os-release
echo "$mem $(nproc) $disk $ID"
CHECK
)
echo "    RAM ${MEM_GB} Go, ${CPUS} vCPU, ${DISK_GB} Go libres, OS ${OS_ID}"
[ "$OS_ID" = "ubuntu" ] || [ "$OS_ID" = "debian" ] || { echo "VM Ubuntu/Debian attendue." >&2; exit 1; }
if [ "$METHOD" = "terraform" ] && [ "$MEM_GB" -lt 6 ]; then
  echo "Moins de 6 Go de RAM : Kubernetes + 8 services ne tiendront pas. Relancer avec --method compose." >&2; exit 1
fi
[ "$DISK_GB" -lt 20 ] && echo "    Attention : moins de 20 Go libres (images Docker + volumes). Agrandir le disque si la construction échoue."
[ -z "$MONITORING" ] && { [ "$MEM_GB" -ge 8 ] && MONITORING=true || MONITORING=false; }
[ -z "$LOGGING" ] && { [ "$MEM_GB" -ge 14 ] && LOGGING=true || LOGGING=false; }
[ "$METHOD" = "terraform" ] || { MONITORING=false; LOGGING=false; }
echo "    Méthode ${METHOD} ; monitoring=${MONITORING} ; logs ELK=${LOGGING} ; domaine ${DOMAIN}"

echo "==> Copie du code commité (git archive HEAD)"
if [ -n "$(git -C "$ROOT" status --porcelain)" ]; then
  echo "    Attention : des modifications non commitées ne seront pas déployées."
fi
ARCHIVE="$(mktemp -t smartrecruit-XXXXXX.tar.gz)"
trap 'rm -f "$ARCHIVE"' EXIT
git -C "$ROOT" archive --format=tar.gz -o "$ARCHIVE" HEAD
scp "${SSH_OPTS[@]}" -q "$ARCHIVE" "$TARGET:/tmp/smartrecruit.tar.gz"
scp "${SSH_OPTS[@]}" -q "$ROOT/deploy/scripts/vm-install.sh" "$TARGET:/tmp/smartrecruit-vm-install.sh"

echo "==> Préparation de la VM et lancement du playbook (20 à 40 min au premier passage)"
remote "METHOD=$METHOD DOMAIN=$DOMAIN MONITORING=$MONITORING LOGGING=$LOGGING REMOTE_DIR=$REMOTE_DIR bash /tmp/smartrecruit-vm-install.sh"

echo
echo "==> Déployé. Adresses :"
case "$METHOD" in
  terraform)
    echo "    Interface et API : http://$DOMAIN/"
    $MONITORING && echo "    Grafana          : http://grafana.$DOMAIN/  (utilisateur admin ; mot de passe, sur la VM :" \
      "kubectl -n monitoring get secret monitoring-grafana -o jsonpath='{.data.admin-password}' | base64 -d)"
    $LOGGING && echo "    Kibana           : http://kibana.$DOMAIN/"
    ;;
  *) echo "    Interface : tunnel SSH nécessaire (ports locaux de la VM), ex. ssh -L 8090:localhost:8090 $TARGET" ;;
esac
