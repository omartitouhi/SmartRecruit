import uuid

from fastapi.testclient import TestClient
from prometheus_client import REGISTRY

from app.main import app
from app.services.cv_store import save_parsed_cv
from app.services.job_analysis_service import save_job_analysis
from app.services.llm_client import LLMError, LLMNotConfiguredError
from tests.samples import CV, EVALUATION, job

client = TestClient(app)


def _store(db_session, cv=CV, offer=None):
    candidate_id = uuid.uuid4()
    offer = offer or job()
    save_parsed_cv(db_session, candidate_id, "0" * 64, cv)
    save_job_analysis(db_session, offer.job_id, offer.title, offer.description, offer.analysis)
    return candidate_id, offer.job_id


def _score(cv_id, job_id):
    return client.post("/api/v1/ai/matching/score", json={"cvId": str(cv_id), "jobId": str(job_id)})


def test_score_follows_the_match_score_contract(stub_llm, db_session):
    cv_id, job_id = _store(db_session)
    stub_llm.reply(EVALUATION)

    response = _score(cv_id, job_id)

    assert response.status_code == 200
    body = response.json()
    assert body["score"] == 79.5
    assert body["criteria"][0] == {
        "label": "Compétences techniques",
        "weight": 0.5,
        "contribution": 37.5,
        "explanation": EVALUATION["skills"]["explanation"],
    }
    assert "Développeur Backend" in stub_llm.calls[0]["user"]


def test_unknown_cv_or_job_gives_404(stub_llm, db_session):
    cv_id, job_id = _store(db_session)
    assert _score(uuid.uuid4(), job_id).status_code == 404
    assert _score(cv_id, uuid.uuid4()).status_code == 404
    assert stub_llm.calls == []


def test_insufficient_data_gives_422_without_calling_the_llm(stub_llm, db_session):
    empty_cv = CV.model_copy(update={"skills": [], "experiences": [], "education": []})
    cv_id, job_id = _store(db_session, cv=empty_cv)

    response = _score(cv_id, job_id)

    assert response.status_code == 422
    assert response.json()["message"].startswith("Données insuffisantes")
    assert stub_llm.calls == []


def test_llm_not_configured_gives_503(stub_llm, db_session):
    stub_llm.error = LLMNotConfiguredError("Service LLM non configuré")
    assert _score(*_store(db_session)).status_code == 503


def test_llm_failure_gives_502(stub_llm, db_session):
    stub_llm.error = LLMError("OpenRouter a répondu 500.")
    response = _score(*_store(db_session))
    assert response.status_code == 502
    assert response.json()["message"] == "OpenRouter a répondu 500."


def test_out_of_range_sub_score_from_the_llm_gives_502(stub_llm, db_session):
    stub_llm.reply({**EVALUATION, "skills": {"subScore": 140, "explanation": "Parfait."}})
    assert _score(*_store(db_session)).status_code == 502


def test_invalid_ids_give_422():
    response = client.post("/api/v1/ai/matching/score", json={"cvId": "abc", "jobId": str(uuid.uuid4())})
    assert response.status_code == 422


def _counter(engine, outcome):
    return REGISTRY.get_sample_value(
        "smartrecruit_ai_match_requests_total", {"engine": engine, "outcome": outcome}
    ) or 0.0


def test_scoring_updates_prometheus_metrics(stub_llm, db_session):
    cv_id, job_id = _store(db_session)
    stub_llm.reply(EVALUATION)
    successes = _counter("llm", "success")
    observed = REGISTRY.get_sample_value("smartrecruit_ai_match_score_count") or 0.0

    assert _score(cv_id, job_id).status_code == 200

    assert _counter("llm", "success") == successes + 1
    assert REGISTRY.get_sample_value("smartrecruit_ai_match_score_count") == observed + 1


def test_insufficient_data_is_counted_separately(stub_llm, db_session):
    empty_cv = CV.model_copy(update={"skills": [], "experiences": [], "education": []})
    cv_id, job_id = _store(db_session, cv=empty_cv)
    before = _counter("llm", "insufficient_data")

    assert _score(cv_id, job_id).status_code == 422

    assert _counter("llm", "insufficient_data") == before + 1
