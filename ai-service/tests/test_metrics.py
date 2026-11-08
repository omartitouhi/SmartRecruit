from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_metrics_endpoint_exposes_prometheus_format():
    client.get("/api/v1/ai/ping")
    response = client.get("/metrics")

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/plain")
    body = response.text
    # Métriques HTTP (Counter + Histogram) et métriques métier déclarées.
    assert "http_requests_total" in body
    assert "http_request_duration_seconds" in body
    assert "smartrecruit_ai_match_requests_total" in body
    assert "smartrecruit_ai_match_score" in body
    assert "smartrecruit_ai_llm_calls_in_progress" in body


def test_probes_are_not_counted_as_traffic():
    client.get("/api/v1/ai/ping")
    body = client.get("/metrics").text

    assert 'handler="/api/v1/ai/ping"' not in body


def test_metrics_endpoint_is_hidden_from_openapi():
    assert "/metrics" not in client.get("/openapi.json").json()["paths"]
