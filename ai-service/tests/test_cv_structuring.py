from datetime import date

from app.schemas.common import CVParsingResult
from app.services.cv_structuring_service import parse_cv_text

CV = """John Doe
Ingenieur logiciel

Compétences
Java, Spring Boot, PostgreSQL, Docker, React

Expérience professionnelle
Backend Developer - Acme (2022-01 - Présent)
- Développement de microservices Spring Boot
Stagiaire Java, Beta Corp, Juin 2020 - Août 2020

Formation
Master Génie Logiciel - ESPRIT, 2025
Licence Informatique - ISI, 2019 - 2022

Langues
Français, English
"""


def test_parse_cv_text_produces_structured_result():
    result = parse_cv_text(CV)
    assert isinstance(result, CVParsingResult)
    assert result.skills == ["Java", "Spring Boot", "PostgreSQL", "Docker", "React", "Microservices"]
    assert result.languages == ["Français", "Anglais"]

    assert len(result.experiences) == 2
    first = result.experiences[0]
    assert (first.title, first.company) == ("Backend Developer", "Acme")
    assert first.start_date == date(2022, 1, 1) and first.end_date is None
    assert "microservices" in first.description
    assert result.experiences[1].start_date == date(2020, 6, 1)

    assert [(e.degree, e.institution, e.year) for e in result.education] == [
        ("Master Génie Logiciel", "ESPRIT", 2025),
        ("Licence Informatique", "ISI", 2022),
    ]


def test_non_conforming_entries_are_rejected_not_invented():
    text = "Expérience\nQuelque chose - 2021 - 2022\nFormation\nUne ligne sans institution 2020"
    result = parse_cv_text(text)
    assert result.experiences == []
    assert result.education == []


def test_text_without_sections_gives_empty_lists():
    result = parse_cv_text("Lorem ipsum dolor sit amet")
    assert (result.skills, result.experiences, result.education, result.languages) == ([], [], [], [])
