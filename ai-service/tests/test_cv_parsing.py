import io

import pytest
from fastapi.testclient import TestClient
from pypdf import PdfWriter
from reportlab.pdfgen import canvas

from app.main import app
from app.services.cv_parsing_service import (
    CVTextExtractionError,
    extract_text_from_pdf,
)
from app.services.llm_client import LLMError, LLMNotConfiguredError

client = TestClient(app)


def _text_pdf_bytes(text: str = "John Doe — Ingenieur logiciel, 5 ans d'experience Java/Spring.") -> bytes:
    buffer = io.BytesIO()
    pdf = canvas.Canvas(buffer)
    pdf.drawString(100, 750, text)
    pdf.save()
    return buffer.getvalue()


def _blank_pdf_bytes() -> bytes:
    """PDF valide mais sans aucune couche de texte — simule un CV scanné/image."""
    writer = PdfWriter()
    writer.add_blank_page(width=200, height=200)
    buffer = io.BytesIO()
    writer.write(buffer)
    return buffer.getvalue()


# --- extract_text_from_pdf (service, AI-CV-01) ---------------------------------


def test_extract_text_from_valid_pdf_returns_the_text():
    text = extract_text_from_pdf(_text_pdf_bytes())
    assert "John Doe" in text


def test_extract_text_from_blank_pdf_raises_explicit_error():
    with pytest.raises(CVTextExtractionError):
        extract_text_from_pdf(_blank_pdf_bytes())


def test_extract_text_from_corrupt_pdf_raises_explicit_error():
    with pytest.raises(CVTextExtractionError):
        extract_text_from_pdf(b"this is not a pdf file")


# --- POST /api/v1/ai/cv-parsing/parse (endpoint) --------------------------------


def _assert_invalid_file(response, message_part: str):
    assert response.status_code == 422
    body = response.json()
    assert body["status"] == 422
    assert body["error"] == "Unprocessable Entity"
    assert body["path"] == "/api/v1/ai/cv-parsing/parse"
    assert body["timestamp"]
    assert message_part in body["message"]


def test_parse_cv_rejects_non_pdf_content_type():
    response = client.post(
        "/api/v1/ai/cv-parsing/parse",
        files={"file": ("cv.txt", b"hello", "text/plain")},
    )
    _assert_invalid_file(response, "PDF")


def test_parse_cv_rejects_oversized_file():
    oversized = b"%PDF-1.4\n" + b"0" * (5 * 1024 * 1024 + 1)
    response = client.post(
        "/api/v1/ai/cv-parsing/parse",
        files={"file": ("cv.pdf", oversized, "application/pdf")},
    )
    _assert_invalid_file(response, "5 Mo")


def test_parse_cv_rejects_empty_file():
    response = client.post(
        "/api/v1/ai/cv-parsing/parse",
        files={"file": ("cv.pdf", b"", "application/pdf")},
    )
    _assert_invalid_file(response, "vide")


def test_parse_cv_rejects_file_that_is_not_really_a_pdf():
    response = client.post(
        "/api/v1/ai/cv-parsing/parse",
        files={"file": ("cv.pdf", b"<html>pas un pdf</html>", "application/pdf")},
    )
    _assert_invalid_file(response, "pas un PDF valide")


def test_parse_cv_rejects_corrupt_pdf():
    response = client.post(
        "/api/v1/ai/cv-parsing/parse",
        files={"file": ("cv.pdf", b"%PDF-1.4 tronque", "application/pdf")},
    )
    _assert_invalid_file(response, "PDF")


def test_parse_cv_without_file_returns_422():
    response = client.post("/api/v1/ai/cv-parsing/parse")
    _assert_invalid_file(response, "file")


def test_parse_cv_returns_explicit_error_for_scanned_pdf():
    response = client.post(
        "/api/v1/ai/cv-parsing/parse",
        files={"file": ("cv.pdf", _blank_pdf_bytes(), "application/pdf")},
    )
    _assert_invalid_file(response, "scan")


def _cv_pdf_bytes() -> bytes:
    buffer = io.BytesIO()
    pdf = canvas.Canvas(buffer)
    lines = ["Competences", "Java, Docker", "Experience", "Dev - Acme (2022-01 - Present)",
             "Formation", "Master GL - ESPRIT, 2025", "Langues", "Francais, Anglais"]
    for i, line in enumerate(lines):
        pdf.drawString(100, 750 - 20 * i, line)
    pdf.save()
    return buffer.getvalue()


def _post_cv():
    return client.post(
        "/api/v1/ai/cv-parsing/parse",
        files={"file": ("cv.pdf", _cv_pdf_bytes(), "application/pdf")},
    )


def test_parse_cv_returns_structured_camelcase_json(stub_llm):
    stub_llm.reply({
        "skills": ["Java", "Docker"],
        "experiences": [{"title": "Dev", "company": "Acme", "startDate": "2022-01-01", "endDate": None,
                         "description": ""}],
        "education": [{"degree": "Master GL", "institution": "ESPRIT", "year": 2025}],
        "languages": ["Français", "Anglais"],
    })

    response = _post_cv()
    assert response.status_code == 200
    body = response.json()
    assert body["skills"] == ["Java", "Docker"]
    assert body["experiences"][0]["startDate"] == "2022-01-01"
    assert body["education"][0]["year"] == 2025
    assert body["languages"] == ["Français", "Anglais"]
    assert "Acme" in stub_llm.calls[0]["user"]


def test_parse_cv_returns_503_when_llm_is_not_configured(stub_llm):
    stub_llm.error = LLMNotConfiguredError("Service LLM non configuré")
    response = _post_cv()
    assert response.status_code == 503
    assert response.json()["message"] == "Service LLM non configuré"


def test_parse_cv_returns_502_when_llm_fails(stub_llm):
    stub_llm.error = LLMError("OpenRouter a répondu 500.")
    response = _post_cv()
    assert response.status_code == 502
    assert response.json()["error"] == "Bad Gateway"
