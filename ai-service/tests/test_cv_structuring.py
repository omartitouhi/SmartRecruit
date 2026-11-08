import asyncio
from datetime import date

import pytest

from app.schemas.common import CVParsingResult
from app.services.cv_structuring_service import parse_cv_text
from app.services.llm_client import LLMError
from tests.conftest import StubLLM

CV = """John Doe
Ingenieur logiciel

Compétences
Java, Spring Boot, PostgreSQL, Docker, React

Expérience professionnelle
Backend Developer - Acme (2022-01 - Présent)
- Développement de microservices Spring Boot

Formation
Master Génie Logiciel - ESPRIT, 2025

Langues
Français, English
"""

LLM_OUTPUT = {
    "skills": ["Java", "Spring Boot", "PostgreSQL", "Docker", "React"],
    "experiences": [
        {"title": "Backend Developer", "company": "Acme", "startDate": "2022-01-01",
         "endDate": None, "description": "Développement de microservices Spring Boot"},
    ],
    "education": [{"degree": "Master Génie Logiciel", "institution": "ESPRIT", "year": 2025}],
    "languages": ["Français", "Anglais"],
}


def _parse(*responses) -> CVParsingResult:
    return asyncio.run(parse_cv_text(CV, StubLLM().reply(*responses)))


def test_llm_output_is_returned_as_structured_result():
    result = _parse(LLM_OUTPUT)
    assert result.skills == ["Java", "Spring Boot", "PostgreSQL", "Docker", "React"]
    assert result.languages == ["Français", "Anglais"]
    assert (result.experiences[0].company, result.experiences[0].start_date) == ("Acme", date(2022, 1, 1))
    assert result.experiences[0].end_date is None
    assert (result.education[0].institution, result.education[0].year) == ("ESPRIT", 2025)


def test_cv_text_is_sent_to_the_llm():
    llm = StubLLM().reply(LLM_OUTPUT)
    asyncio.run(parse_cv_text(CV, llm))
    assert "Backend Developer - Acme" in llm.calls[0]["user"]


def test_values_absent_from_the_cv_are_rejected_not_invented():
    hallucinated = {
        **LLM_OUTPUT,
        "skills": ["Java", "Kubernetes", "Go"],
        "experiences": [
            *LLM_OUTPUT["experiences"],
            {"title": "CTO", "company": "Google", "startDate": "2019-01-01", "endDate": None, "description": ""},
        ],
        "education": [{"degree": "PhD", "institution": "MIT", "year": 2018}],
        "languages": ["Français", "Japonais"],
    }
    result = _parse(hallucinated)
    assert result.skills == ["Java"]
    assert [e.company for e in result.experiences] == ["Acme"]
    assert result.education == []
    assert result.languages == ["Français"]


def test_entries_not_conforming_to_the_schema_are_rejected():
    malformed = {
        **LLM_OUTPUT,
        "experiences": [
            {"title": "Backend Developer", "company": "Acme", "startDate": "début 2022", "endDate": None,
             "description": ""},
            {"title": "Backend Developer", "company": "Acme", "startDate": "2022-01-01", "endDate": None,
             "description": "", "salary": 5000},
        ],
        "education": [{"degree": "Master Génie Logiciel", "institution": "ESPRIT", "year": "vers 2025"}],
    }
    result = _parse(malformed)
    assert result.experiences == []
    assert result.education == []


def test_output_not_matching_the_top_level_schema_raises_explicit_error():
    with pytest.raises(LLMError):
        _parse({"competences": ["Java"]})


def test_non_json_output_raises_explicit_error():
    with pytest.raises(LLMError):
        _parse("Voici le CV : Java, Docker")


def test_duplicate_skills_are_removed():
    result = _parse({**LLM_OUTPUT, "skills": ["Java", "java", "JAVA ", "Docker"]})
    assert result.skills == ["Java", "Docker"]
