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


def test_parse_cv_rejects_non_pdf_content_type():
    response = client.post(
        "/api/v1/ai/cv-parsing/parse",
        files={"file": ("cv.txt", b"hello", "text/plain")},
    )
    assert response.status_code == 415


def test_parse_cv_rejects_oversized_file():
    oversized = b"%PDF-1.4\n" + b"0" * (5 * 1024 * 1024 + 1)
    response = client.post(
        "/api/v1/ai/cv-parsing/parse",
        files={"file": ("cv.pdf", oversized, "application/pdf")},
    )
    assert response.status_code == 413


def test_parse_cv_returns_explicit_error_for_scanned_pdf():
    response = client.post(
        "/api/v1/ai/cv-parsing/parse",
        files={"file": ("cv.pdf", _blank_pdf_bytes(), "application/pdf")},
    )
    assert response.status_code == 422
    assert response.json()["detail"]


def test_parse_cv_returns_structured_camelcase_json():
    buffer = io.BytesIO()
    pdf = canvas.Canvas(buffer)
    lines = ["Competences", "Java, Docker", "Experience", "Dev - Acme (2022-01 - Present)",
             "Formation", "Master GL - ESPRIT, 2025", "Langues", "Francais, Anglais"]
    for i, line in enumerate(lines):
        pdf.drawString(100, 750 - 20 * i, line)
    pdf.save()

    response = client.post(
        "/api/v1/ai/cv-parsing/parse",
        files={"file": ("cv.pdf", buffer.getvalue(), "application/pdf")},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["skills"] == ["Java", "Docker"]
    assert body["experiences"][0]["startDate"] == "2022-01-01"
    assert body["education"][0]["year"] == 2025
    assert body["languages"] == ["Français", "Anglais"]
