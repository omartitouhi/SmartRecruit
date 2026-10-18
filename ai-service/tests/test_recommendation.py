import json
import uuid

from fastapi.testclient import TestClient

from app.main import app
from app.services.cv_store import save_parsed_cv
from app.services.job_analysis_service import save_job_analysis
from app.services.llm_client import LLMError, LLMNotConfiguredError
from app.services.recommendation_service import MAX_CANDIDATE_JOBS, shortlist
from tests.samples import CV, job

client = TestClient(app)


def _store_jobs(db_session, offers):
    for offer in offers:
        save_job_analysis(db_session, offer.job_id, offer.title, offer.description, offer.analysis)


def _candidate(db_session) -> uuid.UUID:
    candidate_id = uuid.uuid4()
    save_parsed_cv(db_session, candidate_id, "0" * 64, CV)
    return candidate_id


def _recommend(candidate_id):
    return client.get(f"/api/v1/ai/recommendation/candidates/{candidate_id}/jobs")


def test_jobs_are_ranked_by_the_llm_following_the_contract(stub_llm, db_session):
    backend, data = job(title="Backend Java"), job(("Python", "Spark"), title="Data Engineer")
    _store_jobs(db_session, [backend, data])
    stub_llm.reply({"recommendations": [
        {"jobId": str(data.job_id), "score": 40},
        {"jobId": str(backend.job_id), "score": 91},
    ]})

    response = _recommend(_candidate(db_session))

    assert response.status_code == 200
    assert response.json() == {"recommendations": [
        {"jobId": str(backend.job_id), "score": 91.0},
        {"jobId": str(data.job_id), "score": 40.0},
    ]}
    prompt = stub_llm.calls[0]["user"]
    assert str(backend.job_id) in prompt and "Data Engineer" in prompt and "Spring Boot" in prompt


def test_job_ids_not_provided_to_the_llm_are_rejected(stub_llm, db_session):
    offer = job()
    _store_jobs(db_session, [offer])
    stub_llm.reply({"recommendations": [
        {"jobId": str(uuid.uuid4()), "score": 99},
        {"jobId": "offre-inventee", "score": 95},
        {"jobId": str(offer.job_id).upper(), "score": 80},
        {"jobId": str(offer.job_id), "score": 10},
    ]})

    body = _recommend(_candidate(db_session)).json()

    assert body == {"recommendations": [{"jobId": str(offer.job_id), "score": 80.0}]}


def test_at_most_ten_jobs_are_returned(stub_llm, db_session):
    offers = [job(title=f"Offre {i}") for i in range(15)]
    _store_jobs(db_session, offers)
    stub_llm.reply({"recommendations": [{"jobId": str(o.job_id), "score": 50 + i} for i, o in enumerate(offers)]})

    recommendations = _recommend(_candidate(db_session)).json()["recommendations"]

    assert len(recommendations) == 10
    assert recommendations[0]["score"] == 64.0
    assert [r["score"] for r in recommendations] == sorted((r["score"] for r in recommendations), reverse=True)


def test_candidate_without_cv_or_without_jobs_gets_an_empty_list(stub_llm, db_session):
    assert _recommend(uuid.uuid4()).json() == {"recommendations": []}
    assert _recommend(_candidate(db_session)).json() == {"recommendations": []}
    assert stub_llm.calls == []


def test_prompt_is_bounded_to_the_best_skill_matches():
    unrelated = [job(("Photoshop",), title=f"Graphiste {i}") for i in range(MAX_CANDIDATE_JOBS)]
    relevant = job(("Java", "Docker"), title="Backend")
    selected = shortlist(CV, [*unrelated, relevant])
    assert len(selected) == MAX_CANDIDATE_JOBS
    assert selected[0] is relevant


def test_out_of_range_score_gives_502(stub_llm, db_session):
    offer = job()
    _store_jobs(db_session, [offer])
    stub_llm.reply(json.dumps({"recommendations": [{"jobId": str(offer.job_id), "score": 150}]}))
    assert _recommend(_candidate(db_session)).status_code == 502


def test_llm_errors_give_502_or_503(stub_llm, db_session):
    _store_jobs(db_session, [job()])
    candidate_id = _candidate(db_session)
    stub_llm.error = LLMError("OpenRouter a répondu 500.")
    assert _recommend(candidate_id).status_code == 502
    stub_llm.error = LLMNotConfiguredError("Service LLM non configuré")
    assert _recommend(candidate_id).status_code == 503
