{{/*
Configuration Filebeat. Défini à part pour servir aussi au checksum du DaemonSet.
*/}}
{{- define "elk.filebeat.config" -}}
filebeat.inputs:
  # Fichiers de logs des conteneurs. Le nom contient le namespace : on ne lit que ceux demandés.
  - type: filestream
    id: kubernetes-containers
    paths:
      {{- if .Values.filebeat.namespaces }}
      {{- range .Values.filebeat.namespaces }}
      - /var/log/containers/*_{{ . }}_*.log
      {{- end }}
      {{- else }}
      - /var/log/containers/*.log
      {{- end }}
    prospector.scanner.symlinks: true
    # Décode le format du runtime (Docker JSON ou CRI de containerd) : message = ligne écrite par l'appli.
    parsers:
      - container: ~
    processors:
      # Ajoute kubernetes.namespace, kubernetes.pod.name, kubernetes.container.name, labels...
      - add_kubernetes_metadata:
          host: ${NODE_NAME}
          matchers:
            - logs_path:
                logs_path: "/var/log/containers/"

{{- if .Values.logstash.enabled }}

# Filebeat collecte, Logstash transforme (JSON ECS, grok nginx/uvicorn) : voir le pipeline Logstash.
output.logstash:
  hosts: ["{{ include "elk.logstash.host" . }}"]
{{- else }}

# Sans Logstash : traitement minimal dans Filebeat puis envoi direct à Elasticsearch.
processors:
  # Logs JSON ECS des services Java (une ligne non JSON est laissée telle quelle).
  - decode_json_fields:
      fields: ["message"]
      target: ""
      overwrite_keys: true
      # "log.level" -> log: { level } (clés à points du format ECS).
      expand_keys: true
      add_error_key: false
  # Nom du service = nom du conteneur, sauf s'il vient déjà du JSON.
  - copy_fields:
      fields:
        - from: kubernetes.container.name
          to: service.name
      fail_on_error: false
      ignore_missing: true

output.elasticsearch:
  hosts: ["{{ include "elk.elasticsearch.url" . }}"]
  index: "{{ .Values.index.prefix }}-%{+yyyy.MM.dd}"

# Index personnalisé : Filebeat exige le nom du modèle et la désactivation de l'ILM (data streams par défaut).
setup.ilm.enabled: false
setup.template.name: "{{ .Values.index.prefix }}"
setup.template.pattern: "{{ .Values.index.prefix }}-*"
setup.template.settings:
  index.number_of_shards: 1
  index.number_of_replicas: 0
{{- end }}

logging.level: info
{{- end -}}
