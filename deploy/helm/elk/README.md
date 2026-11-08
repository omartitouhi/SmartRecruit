# Chart `elk` : logs centralisés de SmartRecruit

```
pods smartrecruit ──> /var/log/containers/*.log ──> Filebeat (DaemonSet, 1 pod/nœud)
                                                        │  beats :5044
                                                        v
                                                    Logstash  (input -> filter -> output)
                                                        │  http :9200
                                                        v
                                                 Elasticsearch (StatefulSet + PVC)
                                                        │
                                                        v
                                                     Kibana :5601 (Discover, data view smartrecruit-*)
```

| Composant | Ressource | Service | Rôle |
|---|---|---|---|
| Filebeat | DaemonSet + RBAC | — | lit les logs des conteneurs, ajoute les métadonnées Kubernetes |
| Logstash | Deployment | `elk-logstash:5044` | JSON ECS (Java), grok nginx (frontend) et uvicorn (IA), champ `service.name` |
| Elasticsearch | StatefulSet (nœud unique) | `elk-elasticsearch:9200` | index quotidiens `smartrecruit-AAAA.MM.JJ` |
| Kibana | Deployment (+ Ingress) | `elk-kibana:5601` | recherche et visualisation |

Toute la pile utilise la même version Elastic (`appVersion` de `Chart.yaml`).

## Installation

Normalement faite par Terraform. À la main :

```bash
helm install elk ./helm/elk -n logging --create-namespace -f deploy/helm/elk/values-dev.yaml --timeout 15m
helm upgrade elk ./helm/elk -n logging -f deploy/helm/elk/values-dev.yaml --timeout 15m
helm history elk -n logging
helm rollback elk 1 -n logging
helm uninstall elk -n logging        # le PVC d'Elasticsearch reste : kubectl -n logging delete pvc -l app.kubernetes.io/name=elasticsearch
```

`--timeout 15m` : un Job (hook `post-install`/`post-upgrade`) attend qu'Elasticsearch et Kibana soient prêts, crée le
modèle d'index (0 réplique) et la data view `smartrecruit-*`. Au premier démarrage, le téléchargement des images peut
dépasser les 5 minutes par défaut de Helm.

Valeurs utiles : `kibana.ingress.enabled` / `kibana.ingress.host` / `kibana.ingress.className`, `filebeat.namespaces`,
`logstash.enabled`, `elasticsearch.persistence.size`, `elasticsearch.javaOpts`,
`elasticsearch.sysctlInitContainer.enabled` (à désactiver si les pods privilégiés sont interdits).

## Voir les logs dans Kibana

1. Ouvrir Kibana : `http://kibana.smartrecruit.local` (dev, ajouter `127.0.0.1 kibana.smartrecruit.local` au fichier hosts)
   ou `kubectl -n logging port-forward svc/elk-kibana 5601:5601` puis `http://localhost:5601`.
2. Menu **Discover**, data view **SmartRecruit** (`smartrecruit-*`), choisir la période.
3. Exemples de requêtes KQL :

```
service.name : "auth-service" and log.level : "ERROR"
service.name : "frontend" and http.response.status_code >= 500
service.name : "ai-service" and message : *ping*
kubernetes.pod.name : user-service-* and not log.level : "INFO"
```

Les services Java écrivent déjà du JSON ECS : `service.name`, `log.level`, `log.logger`, `message` sont des champs
du log d'origine. Pour les autres conteneurs, `service.name` vient du nom du conteneur.

## Filebeat seul ou Filebeat + Logstash ?

- **Filebeat + Logstash** (`logstash.enabled: true`, défaut) : Filebeat reste un agent léger sur chaque nœud, et
  Logstash centralise les transformations lourdes (grok, date, enrichissements). Il faut une JVM de plus.
- **Filebeat seul** (`logstash.enabled: false`) : moins de ressources. Filebeat décode le JSON et renseigne
  `service.name`, mais les journaux nginx et uvicorn restent en texte brut (pas de grok).

## Raccourcis de lab (à revoir en production)

- Sécurité Elasticsearch désactivée (`xpack.security.enabled=false`) : HTTP sans authentification, joignable
  uniquement dans le cluster. En prod, Kibana est protégé par l'authentification basique de l'Ingress
  (`values-prod.yaml`), mais il faudrait activer xpack.security.
- Elasticsearch sur un seul nœud, sans réplique ni politique de rétention (ILM) : supprimer les vieux index à la main
  (`curl -X DELETE localhost:9200/smartrecruit-2026.10.*`).
