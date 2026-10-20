"""Parcours complet par HTTP avec le moteur à règles : aucune clé OpenRouter, aucun appel réseau."""

import io
import uuid

import pytest
from fastapi.testclient import TestClient
from reportlab.pdfgen import canvas

from app.core.config import settings
from app.main import app

client = TestClient(app)

CV_LINES = [
    "Sami Ben Ali", "Competences", "Java, Spring Boot, Postgres, Docker", "Experience professionnelle",
    "Backend Developer - Acme Tech (2019-01 - Present)", "Formation", "Master Genie Logiciel - ESPRIT, 2018",
    "Langues", "Francais, Anglais",
]


@pytest.fixture(autouse=True)
def rules_engine(monkeypatch):
    monkeypatch.setattr(settings, "ai_engine", "rules")
    monkeypatch.setattr(settings, "openrouter_api_key", None)


def _pdf() -> bytes:
    buffer = io.BytesIO()
    pdf = canvas.Canvas(buffer)
    for i, line in enumerate(CV_LINES):
        pdf.drawString(60, 800 - 20 * i, line)
    pdf.save()
    return buffer.getvalue()


def test_cv_job_match_gap_and_recommendation_work_without_llm():
    candidate, job_id = str(uuid.uuid4()), str(uuid.uuid4())

    parsed = client.post("/api/v1/ai/cv-parsing/parse", data={"candidateId": candidate},
                         files={"file": ("cv.pdf", _pdf(), "application/pdf")})
    assert parsed.status_code == 200
    assert parsed.json()["skills"][:3] == ["Java", "Spring Boot", "PostgreSQL"]
    assert parsed.json()["experiences"][0]["company"] == "Acme Tech"

    analysis = client.post("/api/v1/ai/job-analysis/analyze", json={
        "jobId": job_id, "title": "Développeur Backend Senior",
        "description": "Java, Spring Boot, PostgreSQL et Kubernetes. 5 ans d'expérience minimum."})
    assert analysis.status_code == 200
    assert analysis.json()["experienceLevel"] == "SENIOR"

    match = client.post("/api/v1/ai/matching/score", json={"cvId": candidate, "jobId": job_id})
    assert match.status_code == 200
    assert len(match.json()["criteria"]) == 3 and 0 < match.json()["score"] <= 100

    gap = client.get(f"/api/v1/ai/skill-gap/candidates/{candidate}/jobs/{job_id}")
    assert gap.json() == {"missingSkills": ["Kubernetes"]}

    recommendation = client.get(f"/api/v1/ai/recommendation/candidates/{candidate}/jobs")
    assert [r["jobId"] for r in recommendation.json()["recommendations"]] == [job_id]
