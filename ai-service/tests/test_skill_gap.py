import uuid

from fastapi.testclient import TestClient

from app.main import app
from app.services.cv_store import save_parsed_cv
from app.services.job_analysis_service import save_job_analysis
from app.services.llm_client import LLMError, LLMNotConfiguredError
from tests.samples import CV, job

client = TestClient(app)


def _store(db_session, offer=None):
    candidate_id = uuid.uuid4()
    offer = offer or job(("Java", "Spring Boot", "PostgreSQL", "Kubernetes", "GraphQL"))
    save_parsed_cv(db_session, candidate_id, "0" * 64, CV)
    save_job_analysis(db_session, offer.job_id, offer.title, offer.description, offer.analysis)
    return candidate_id, offer.job_id


def _gap(candidate_id, job_id):
    return client.get(f"/api/v1/ai/skill-gap/candidates/{candidate_id}/jobs/{job_id}")


def test_missing_required_skills_are_returned(stub_llm, db_session):
    stub_llm.reply({"missingSkills": ["Kubernetes", "GraphQL"]})

    response = _gap(*_store(db_session))

    assert response.status_code == 200
    assert response.json() == {"missingSkills": ["Kubernetes", "GraphQL"]}
    prompt = stub_llm.calls[0]["user"]
    assert "Postgres" in prompt and "PostgreSQL" in prompt


def test_skills_not_in_the_offer_are_rejected(stub_llm, db_session):
    stub_llm.reply({"missingSkills": ["Kubernetes", "Terraform", "Rust"]})
    assert _gap(*_store(db_session)).json() == {"missingSkills": ["Kubernetes"]}


def test_skills_keep_the_offer_spelling_without_duplicates(stub_llm, db_session):
    stub_llm.reply({"missingSkills": ["kubernetes", "KUBERNETES", "graph ql"]})
    assert _gap(*_store(db_session)).json() == {"missingSkills": ["Kubernetes", "GraphQL"]}


def test_skill_listed_verbatim_in_the_cv_cannot_be_missing(stub_llm, db_session):
    stub_llm.reply({"missingSkills": ["Java", "Kubernetes"]})
    assert _gap(*_store(db_session)).json() == {"missingSkills": ["Kubernetes"]}


def test_offer_without_required_skills_gives_empty_list_without_llm(stub_llm, db_session):
    assert _gap(*_store(db_session, job(required_skills=()))).json() == {"missingSkills": []}
    assert stub_llm.calls == []


def test_unknown_candidate_or_job_gives_404(stub_llm, db_session):
    candidate_id, job_id = _store(db_session)
    assert _gap(uuid.uuid4(), job_id).status_code == 404
    assert _gap(candidate_id, uuid.uuid4()).status_code == 404


def test_non_conforming_output_gives_502(stub_llm, db_session):
    stub_llm.reply({"missingSkills": "Kubernetes"})
    assert _gap(*_store(db_session)).status_code == 502


def test_llm_errors_give_502_or_503(stub_llm, db_session):
    ids = _store(db_session)
    stub_llm.error = LLMError("OpenRouter a répondu 500.")
    assert _gap(*ids).status_code == 502
    stub_llm.error = LLMNotConfiguredError("Service LLM non configuré")
    assert _gap(*ids).status_code == 503
