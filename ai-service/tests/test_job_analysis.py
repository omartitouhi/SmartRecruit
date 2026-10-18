import uuid

from fastapi.testclient import TestClient

from app.main import app
from app.models import JobAnalysis
from app.services.llm_client import LLMError, LLMNotConfiguredError

client = TestClient(app)

DESCRIPTION = (
    "Nous recherchons un développeur backend senior (5 ans minimum) maîtrisant Java, "
    "Spring Boot et PostgreSQL. Une expérience de Docker est un plus."
)
LLM_OUTPUT = {
    "requiredSkills": ["Java", "Spring Boot", "PostgreSQL", "Docker"],
    "experienceLevel": "SENIOR",
    "seniority": "SENIOR",
}


def _analyze(job_id=None, description=DESCRIPTION, **extra):
    return client.post(
        "/api/v1/ai/job-analysis/analyze",
        json={"jobId": str(job_id or uuid.uuid4()), "description": description, **extra},
    )


def test_job_description_is_analysed_by_the_llm(stub_llm):
    stub_llm.reply(LLM_OUTPUT)

    response = _analyze(title="Développeur Backend")

    assert response.status_code == 200
    assert response.json() == LLM_OUTPUT
    assert "Développeur Backend" in stub_llm.calls[0]["user"]
    assert "Spring Boot et PostgreSQL" in stub_llm.calls[0]["user"]


def test_analysis_is_stored_for_matching(stub_llm, db_session):
    job_id = uuid.uuid4()
    stub_llm.reply(LLM_OUTPUT, {**LLM_OUTPUT, "requiredSkills": ["Java"]})

    _analyze(job_id, title="Développeur Backend")
    stored = db_session.get(JobAnalysis, job_id)
    assert (stored.title, stored.result["experienceLevel"]) == ("Développeur Backend", "SENIOR")

    _analyze(job_id)
    db_session.expire_all()
    assert db_session.get(JobAnalysis, job_id).result["requiredSkills"] == ["Java"]


def test_skills_absent_from_the_offer_are_rejected(stub_llm):
    stub_llm.reply({**LLM_OUTPUT, "requiredSkills": ["Java", "Kubernetes", "Go"]})
    assert _analyze().json()["requiredSkills"] == ["Java"]


def test_fully_hallucinated_skills_give_502(stub_llm, db_session):
    stub_llm.reply({**LLM_OUTPUT, "requiredSkills": ["Kubernetes", "Rust"]})
    assert _analyze().status_code == 502
    assert db_session.query(JobAnalysis).count() == 0


def test_output_not_matching_the_schema_gives_502_and_nothing_is_stored(stub_llm, db_session):
    stub_llm.reply({**LLM_OUTPUT, "experienceLevel": "EXPERT"})
    response = _analyze()
    assert response.status_code == 502
    assert "schéma" in response.json()["message"]
    assert db_session.query(JobAnalysis).count() == 0


def test_unknown_field_in_llm_output_gives_502(stub_llm):
    stub_llm.reply({**LLM_OUTPUT, "salary": "50k"})
    assert _analyze().status_code == 502


def test_llm_failure_gives_502(stub_llm):
    stub_llm.error = LLMError("OpenRouter a répondu 500.")
    assert _analyze().status_code == 502


def test_llm_not_configured_gives_503(stub_llm):
    stub_llm.error = LLMNotConfiguredError("Service LLM non configuré")
    assert _analyze().status_code == 503


def test_invalid_request_gives_422(stub_llm):
    assert _analyze(description="trop court").status_code == 422
    response = client.post("/api/v1/ai/job-analysis/analyze", json={"jobId": "pas-un-uuid", "description": DESCRIPTION})
    assert response.status_code == 422
    assert stub_llm.calls == []
