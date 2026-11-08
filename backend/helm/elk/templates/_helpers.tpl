{{/*
Fonctions communes aux templates du chart ELK.
Les composants sont appelés avec un dict : (dict "ctx" $ "component" "elasticsearch").
*/}}

{{/* Nom et version du chart (label helm.sh/chart). */}}
{{- define "elk.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Nom d'une ressource : <release>-<composant>, ex. elk-elasticsearch. */}}
{{- define "elk.componentName" -}}
{{- printf "%s-%s" .ctx.Release.Name .component | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Labels de sélection (immuables : ne pas y ajouter la version). */}}
{{- define "elk.selectorLabels" -}}
app.kubernetes.io/name: {{ .component }}
app.kubernetes.io/instance: {{ .ctx.Release.Name }}
{{- end -}}

{{/* Labels communs à toutes les ressources. */}}
{{- define "elk.labels" -}}
helm.sh/chart: {{ include "elk.chart" .ctx }}
{{ include "elk.selectorLabels" . }}
app.kubernetes.io/version: {{ .ctx.Chart.AppVersion | quote }}
app.kubernetes.io/component: logging
app.kubernetes.io/part-of: smartrecruit
app.kubernetes.io/managed-by: {{ .ctx.Release.Service }}
{{- end -}}

{{/* Image : <repository>:<tag>, le tag par défaut étant la version Elastic du chart. */}}
{{- define "elk.image" -}}
{{- printf "%s:%s" .image.repository (.image.tag | default .ctx.Chart.AppVersion) -}}
{{- end -}}

{{/* Adresses internes (même namespace que la release). */}}
{{- define "elk.elasticsearch.url" -}}
{{- printf "http://%s:9200" (include "elk.componentName" (dict "ctx" . "component" "elasticsearch")) -}}
{{- end -}}

{{- define "elk.kibana.url" -}}
{{- printf "http://%s:5601" (include "elk.componentName" (dict "ctx" . "component" "kibana")) -}}
{{- end -}}

{{- define "elk.logstash.host" -}}
{{- printf "%s:5044" (include "elk.componentName" (dict "ctx" . "component" "logstash")) -}}
{{- end -}}
