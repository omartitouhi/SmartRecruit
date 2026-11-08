import io
import uuid

from fastapi.testclient import TestClient
from reportlab.pdfgen import canvas

from app.main import app
from app.models import ParsedCV

client = TestClient(app)

LLM_OUTPUT = {
    "skills": ["Java", "Docker"],
    "experiences": [],
    "education": [],
    "languages": [],
}


def _pdf(text: str) -> bytes:
    buffer = io.BytesIO()
    pdf = canvas.Canvas(buffer)
    pdf.drawString(100, 750, text)
    pdf.save()
    return buffer.getvalue()


def _parse(pdf: bytes, candidate_id: uuid.UUID | None = None):
    return client.post(
        "/api/v1/ai/cv-parsing/parse",
        files={"file": ("cv.pdf", pdf, "application/pdf")},
        data={"candidateId": str(candidate_id)} if candidate_id else None,
    )


def test_parsed_cv_is_stored_and_linked_to_the_candidate(stub_llm, db_session):
    candidate_id = uuid.uuid4()
    stub_llm.reply(LLM_OUTPUT)

    assert _parse(_pdf("Competences : Java, Docker"), candidate_id).status_code == 200

    stored = db_session.get(ParsedCV, candidate_id)
    assert stored.result["skills"] == ["Java", "Docker"]
    assert len(stored.file_sha256) == 64 and stored.parsed_at is not None

    response = client.get(f"/api/v1/ai/cv-parsing/candidates/{candidate_id}")
    assert response.status_code == 200
    assert response.json()["skills"] == ["Java", "Docker"]


def test_same_cv_is_not_analysed_twice(stub_llm):
    candidate_id = uuid.uuid4()
    pdf = _pdf("Competences : Java, Docker")
    stub_llm.reply(LLM_OUTPUT)

    first = _parse(pdf, candidate_id)
    second = _parse(pdf, candidate_id)

    assert second.status_code == 200 and second.json() == first.json()
    assert len(stub_llm.calls) == 1


def test_new_cv_replaces_the_previous_result(stub_llm):
    candidate_id = uuid.uuid4()
    stub_llm.reply(LLM_OUTPUT, {**LLM_OUTPUT, "skills": ["Python"]})

    _parse(_pdf("Competences : Java, Docker"), candidate_id)
    _parse(_pdf("Competences : Python"), candidate_id)

    assert len(stub_llm.calls) == 2
    response = client.get(f"/api/v1/ai/cv-parsing/candidates/{candidate_id}")
    assert response.json()["skills"] == ["Python"]


def test_parsing_without_candidate_id_stores_nothing(stub_llm, db_session):
    stub_llm.reply(LLM_OUTPUT)
    assert _parse(_pdf("Competences : Java, Docker")).status_code == 200
    assert db_session.query(ParsedCV).count() == 0


def test_failed_parsing_stores_nothing(stub_llm, db_session):
    stub_llm.reply({"unexpected": True})
    assert _parse(_pdf("Competences : Java, Docker"), uuid.uuid4()).status_code == 502
    assert db_session.query(ParsedCV).count() == 0


def test_unknown_candidate_returns_404():
    response = client.get(f"/api/v1/ai/cv-parsing/candidates/{uuid.uuid4()}")
    assert response.status_code == 404
    assert response.json()["message"] == "Aucun CV analysé pour ce candidat."


def test_rgpd_erasure_removes_candidate_data_and_is_idempotent(stub_llm, db_session):
    candidate_id = uuid.uuid4()
    stub_llm.reply(LLM_OUTPUT)
    _parse(_pdf("Competences : Java, Docker"), candidate_id)

    assert client.delete(f"/api/v1/ai/candidates/{candidate_id}/data").status_code == 204
    assert db_session.get(ParsedCV, candidate_id) is None
    assert client.delete(f"/api/v1/ai/candidates/{candidate_id}/data").status_code == 204
