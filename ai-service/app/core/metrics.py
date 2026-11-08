"""Métriques Prometheus du service IA (exposées sur /metrics avec les métriques HTTP).

Les métriques HTTP standard viennent de prometheus-fastapi-instrumentator :
  - http_requests_total            Counter   requêtes reçues (handler, method, status)
  - http_request_duration_seconds  Histogram durée de traitement (buckets)

Métriques métier ci-dessous, une par type Prometheus :
  - Counter   : ne fait qu'augmenter (rate() dans Prometheus)
  - Histogram : répartit les observations dans des buckets (histogram_quantile())
  - Gauge     : valeur instantanée qui monte et descend
"""

from prometheus_client import Counter, Gauge, Histogram

MATCH_REQUESTS = Counter(
    "smartrecruit_ai_match_requests_total",
    "Nombre de calculs de score de compatibilité CV/offre.",
    ["engine", "outcome"],
)

MATCH_SCORE = Histogram(
    "smartrecruit_ai_match_score",
    "Répartition des scores de compatibilité calculés (0 à 100).",
    buckets=(10, 20, 30, 40, 50, 60, 70, 80, 90, 100),
)

LLM_CALLS_IN_PROGRESS = Gauge(
    "smartrecruit_ai_llm_calls_in_progress",
    "Appels OpenRouter en cours.",
)
