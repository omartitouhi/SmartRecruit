{{/*
Pipeline Logstash (input -> filter -> output). Défini à part pour servir aussi au checksum du Deployment.
*/}}
{{- define "elk.logstash.pipeline" -}}
# --- Entrées : les événements envoyés par Filebeat (protocole Beats).
input {
  beats {
    port => 5044
  }
}

# --- Filtres : un traitement par type de log.
filter {
  if [message] =~ /^\{/ {
    # Services Java : une ligne = un document JSON au format ECS (Spring Boot structured logging).
    # Le JSON remplace message, @timestamp, log.level, service.name...
    json {
      source => "message"
      skip_on_invalid_json => true
    }
    # Le format ECS peut écrire des clés à points ("log.level", "service.name") : on les transforme
    # en objets imbriqués ([log][level]) pour les conditions ci-dessous et pour Elasticsearch.
    de_dot {
      nested => true
    }
  } else if [kubernetes][container][name] == "frontend" {
    # Frontend nginx : journal d'accès au format "combined" (même format qu'Apache dans le lab).
    grok {
      match => { "message" => "%{COMBINEDAPACHELOG}" }
      tag_on_failure => ["_nginx_grok_failure"]
    }
    if [timestamp] {
      # Date de la requête plutôt que date de lecture du fichier.
      date {
        match => ["timestamp", "dd/MMM/yyyy:HH:mm:ss Z"]
        remove_field => ["timestamp"]
      }
    }
    if "_nginx_grok_failure" not in [tags] {
      mutate { add_field => { "[log][level]" => "INFO" } }
    }
  } else if [kubernetes][container][name] == "ai-service" {
    # Service IA (uvicorn) : "INFO:     10.0.0.1:1234 - "GET /api/v1/ai/ping HTTP/1.1" 200 OK".
    grok {
      match => { "message" => "^%{LOGLEVEL:[log][level]}:\s+%{GREEDYDATA:message}" }
      overwrite => ["message"]
      tag_on_failure => []
    }
  }

  # Nom du service : celui du JSON ECS s'il existe, sinon le nom du conteneur Kubernetes.
  if ![service][name] and [kubernetes][container][name] {
    mutate { add_field => { "[service][name]" => "%{[kubernetes][container][name]}" } }
  }
  if [log][level] {
    mutate { uppercase => ["[log][level]"] }
  }
}

# --- Sorties : un index par jour, ex. {{ .Values.index.prefix }}-2026.11.08.
output {
  elasticsearch {
    hosts => ["{{ include "elk.elasticsearch.url" . }}"]
    index => "{{ .Values.index.prefix }}-%{+YYYY.MM.dd}"
    # Le modèle d'index (0 réplique) est créé par le Job data-view du chart.
    manage_template => false
  }
}
{{- end -}}
