{{/*
Fonctions réutilisables par tous les templates du chart (cf. cours : _helpers.tpl).
*/}}

{{/* Nom du chart, surchargeable par nameOverride. */}}
{{- define "smartrecruit.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Nom complet : préfixe des objets partagés (ConfigMap, Secret...). */}}
{{- define "smartrecruit.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/* chart-version, pour le label helm.sh/chart. */}}
{{- define "smartrecruit.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Labels communs à tous les objets. */}}
{{- define "smartrecruit.labels" -}}
helm.sh/chart: {{ include "smartrecruit.chart" . }}
app.kubernetes.io/part-of: smartrecruit
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}

{{/*
Labels d'un composant. Appel : include "smartrecruit.componentLabels" (dict "root" $ "name" "auth-service")
*/}}
{{- define "smartrecruit.componentLabels" -}}
{{ include "smartrecruit.labels" .root }}
app.kubernetes.io/name: {{ .name }}
{{- end }}

{{/* Labels de sélection (immuables : ne jamais y ajouter la version). */}}
{{- define "smartrecruit.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end }}

{{/* Nom de la ConfigMap applicative. */}}
{{- define "smartrecruit.configName" -}}
{{- printf "%s-config" (include "smartrecruit.fullname" .) }}
{{- end }}

{{/* Nom du Secret applicatif (créé par le chart ou existant). */}}
{{- define "smartrecruit.secretName" -}}
{{- if .Values.secrets.create }}
{{- printf "%s-secrets" (include "smartrecruit.fullname" .) }}
{{- else }}
{{- required "secrets.existingSecret est obligatoire quand secrets.create=false" .Values.secrets.existingSecret }}
{{- end }}
{{- end }}

{{/*
Image d'un service : <registry>/<prefix><service>:<tag> (registry vide => image locale).
Appel : include "smartrecruit.image" (dict "root" $ "name" "auth-service")
*/}}
{{- define "smartrecruit.image" -}}
{{- $img := .root.Values.images }}
{{- $tag := default .root.Chart.AppVersion $img.tag }}
{{- if $img.registry }}
{{- printf "%s/%s%s:%s" (trimSuffix "/" $img.registry) $img.prefix .name $tag }}
{{- else }}
{{- printf "%s%s:%s" $img.prefix .name $tag }}
{{- end }}
{{- end }}

{{/* Variable d'environnement lue dans le Secret applicatif. */}}
{{- define "smartrecruit.secretEnv" -}}
- name: {{ .key }}
  valueFrom:
    secretKeyRef:
      name: {{ include "smartrecruit.secretName" .root }}
      key: {{ .key }}
{{- end }}
