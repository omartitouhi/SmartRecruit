# SmartRecruit avec Ansible

Ce playbook prépare une machine Ubuntu (VM, serveur ou WSL2) puis y déploie SmartRecruit. Il installe Docker et les outils
Kubernetes (kubectl, kind, Helm, Terraform), clone les trois dépôts et lance le déploiement.

```
ansible/
  ansible.cfg                        configuration du projet (surcharge /etc/ansible/ansible.cfg : inventaire, rôles)
  requirements.yml                   collections community.general et community.docker
  inventory/hosts.ini                machines, rangées par groupes (smartrecruit_local, smartrecruit_vms -> smartrecruit)
  inventory/group_vars/all/main.yml  variables : versions des outils, dépôts, méthode de déploiement...
  inventory/group_vars/all/vault.yml secrets chiffrés avec ansible-vault (à créer depuis vault.yml.example, ignoré par git)
  site.yml                           playbook : un jeu, quatre rôles
  roles/
    common/       paquets de base (apt), fuseau horaire, architecture, dossier personnel de l'utilisateur
    docker/       dépôt officiel Docker, Docker Engine + Compose, daemon.json (template Jinja2) -> handler « Redémarrer docker »
    k8s_tools/    kubectl, kind, Helm, Terraform aux versions épinglées (sommes de contrôle vérifiées pour kubectl et kind)
    smartrecruit/ clonage des dépôts, .env et terraform.tfvars (templates), /etc/hosts, déploiement, vérification
```

## Machine de contrôle

Ansible ne tourne pas sous Windows comme machine de contrôle : utiliser Linux, macOS ou **WSL2** (Ubuntu).

```bash
sudo apt update && sudo apt install -y pipx && pipx install --include-deps ansible
cd smartrecruit-backend/ansible
ansible-galaxy collection install -r requirements.yml
```

Les machines gérées n'ont besoin que de SSH et de Python 3 : Ansible fonctionne sans agent.

## Inventaire

- **Machine locale** (WSL2 ou Ubuntu) : le groupe `smartrecruit_local` contient `localhost` avec `ansible_connection=local`, sans SSH.
- **VM distantes** : décommenter les lignes de `[smartrecruit_vms]` dans `inventory/hosts.ini`, puis copier la clé SSH publique :

```bash
ssh-keygen -t ed25519          # si vous n'avez pas encore de clé
ssh-copy-id vagrant@192.168.56.10
```

Commandes ad hoc pour vérifier la connexion :

```bash
ansible all -m ping
ansible smartrecruit -m ansible.builtin.setup -a "filter=ansible_distribution*"
ansible smartrecruit -b -m ansible.builtin.apt -a "name=htop state=present update_cache=true"
```

## Secrets (ansible-vault)

```bash
cp inventory/group_vars/all/vault.yml.example inventory/group_vars/all/vault.yml
ansible-vault encrypt inventory/group_vars/all/vault.yml     # choisir un mot de passe
ansible-vault edit inventory/group_vars/all/vault.yml        # modifier plus tard
```

Sans `vault.yml`, le playbook prend les secrets de développement : mot de passe admin `Admin-Dev-2026!` et pas de clé OpenRouter.
Les tâches qui écrivent des secrets (`.env`, `terraform.tfvars`) sont en `no_log` et sans diff.

## Lancer le playbook

Ne pas lancer `ansible-playbook` avec `sudo` : l'utilisateur courant devient l'utilisateur d'exploitation (groupe docker,
kubeconfig). Les droits root sont demandés par `become` (option `-K` pour saisir le mot de passe sudo).

```bash
ansible-playbook site.yml -K                         # sans vault
ansible-playbook site.yml -K --ask-vault-pass        # avec vault.yml chiffré
ansible-playbook site.yml -K --check --diff          # simulation : ce qui changerait, sans rien modifier
ansible-playbook site.yml -K --tags docker,tools     # seulement certains rôles (common, docker, tools, deploy)
ansible-playbook site.yml -K -e smartrecruit_deploy_method=compose
ansible-playbook site.yml -K --limit vm1             # une seule machine
```

Le playbook est idempotent. Une seconde exécution doit afficher `changed=0`, sauf si un dépôt ou une variable a changé.
`--check` ne lance ni les constructions ni les commandes de déploiement ; Terraform s'y limite à un `plan`.

## Méthodes de déploiement (`smartrecruit_deploy_method`)

| Méthode | Ce qui est lancé | Adresse vérifiée |
|---|---|---|
| `terraform` (défaut) | `docker compose build`, puis Terraform dans `terraform/` : cluster kind, chart Helm, Prometheus/Grafana, ELK. Ansible écrit `terraform/terraform.tfvars`, lu automatiquement par Terraform ; l'état est dans `terraform/state/terraform.tfstate` | http://smartrecruit.local |
| `compose` | `docker compose build`, puis `docker compose up -d --wait` | http://localhost:5173 |
| `kind-script` | `docker compose build`, puis `k8s/scripts/dev.sh up` (Kustomize, overlay dev) | http://localhost:8090 |

Avec `terraform`, le playbook ajoute `smartrecruit.local`, `grafana.smartrecruit.local` et `kibana.smartrecruit.local` dans
`/etc/hosts` (127.0.0.1). Sous WSL2, ce fichier est régénéré au redémarrage si `generateHosts` vaut `true` dans `/etc/wsl.conf`.
Depuis le navigateur Windows, ajouter aussi ces noms dans `C:\Windows\System32\drivers\etc\hosts`.

Les commandes de déploiement tournent en root : l'ajout au groupe docker ne s'applique qu'à la prochaine connexion de
l'utilisateur. Le kubeconfig est écrit dans `~/.kube/config` de l'utilisateur, puis le kubeconfig et l'état Terraform lui
sont rendus (`chown`). Pour utiliser `docker` sans `sudo`, se déconnecter puis se reconnecter (ou lancer `newgrp docker`).

## Notions du cours utilisées

| Notion | Où |
|---|---|
| Inventaire, groupes, groupe parent `:children` | `inventory/hosts.ini` |
| `ansible.cfg` du projet qui surcharge `inventory` | `ansible.cfg` |
| `become` (élévation de privilèges) sur tout le jeu, `become_user` sur une tâche | `site.yml`, `roles/smartrecruit/tasks/main.yml` (git) |
| Variables (`group_vars`, `defaults`), secrets avec `ansible-vault` | `inventory/group_vars/all/`, `roles/*/defaults/` |
| Module `apt` : `state`, `update_cache` | `roles/common`, `roles/docker` |
| Templates Jinja2 | `daemon.json.j2`, `env.j2`, `terraform.tfvars.j2` |
| Module `service`, `notify` et `handlers` | `roles/docker/tasks/main.yml`, `roles/docker/handlers/main.yml` |
| Rôles, tags | `roles/`, `site.yml` |
