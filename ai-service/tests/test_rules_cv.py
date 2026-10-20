from datetime import date

from app.services.rules_cv import NOT_SPECIFIED, parse_cv_rules

SECTIONED = """Sami Ben Ali
Ingenieur logiciel - Tunis
Competences
Java, Spring Boot, Postgres, Docker
Langages : TypeScript, Cucumber
Experience professionnelle
Backend Developer - Acme Tech (2022-01 - Present)
Developpement de microservices Spring Boot
Stagiaire Java - Beta Soft (2021-06 - 2021-09)
Formation
Master Genie Logiciel - ESPRIT, 2025
Licence Informatique - ISI, 2022
Langues
Francais, Anglais
"""

ABOVE_THE_DATES = """EXPÉRIENCE PROFESSIONNELLE
Développeur Full Stack chez Vermeg
Janvier 2021 - Présent
• API REST avec Node.js
Stage PFE | Wevioo
06/2020 - 09/2020
FORMATION
Diplôme d'ingénieur en informatique, INSAT 2020
Licence en informatique - ISET Sfax (2015 - 2018)
COMPÉTENCES
Langues : Arabe, Français
"""


def test_skills_come_from_the_catalog_and_the_skills_section_without_duplicates():
    cv = parse_cv_rules(SECTIONED)
    assert cv.skills[:4] == ["Java", "Spring Boot", "PostgreSQL", "Docker"]
    assert "TypeScript" in cv.skills and "Cucumber" in cv.skills
    assert len(cv.skills) == len({s.lower() for s in cv.skills})


def test_experiences_have_title_company_dates_and_description():
    first, second = parse_cv_rules(SECTIONED).experiences
    assert (first.title, first.company, first.start_date, first.end_date) == (
        "Backend Developer", "Acme Tech", date(2022, 1, 1), None)
    assert first.description == "Developpement de microservices Spring Boot"
    assert (second.title, second.company, second.end_date) == ("Stagiaire Java", "Beta Soft", date(2021, 9, 1))


def test_education_uses_the_year_of_its_own_line():
    cv = parse_cv_rules(SECTIONED)
    assert [(e.degree, e.institution, e.year) for e in cv.education] == [
        ("Master Genie Logiciel", "ESPRIT", 2025), ("Licence Informatique", "ISI", 2022)]


def test_languages_are_named_in_french_and_not_listed_as_skills():
    assert parse_cv_rules(SECTIONED).languages == ["Français", "Anglais"]
    cv = parse_cv_rules(ABOVE_THE_DATES)
    assert cv.languages == ["Français", "Arabe"] and "Arabe" not in cv.skills


def test_a_job_line_above_the_dates_and_each_entry_keeps_its_own_description():
    first, second = parse_cv_rules(ABOVE_THE_DATES).experiences
    assert (first.title, first.company, first.start_date) == ("Développeur Full Stack", "Vermeg", date(2021, 1, 1))
    assert first.description == "API REST avec Node.js"
    assert (second.title, second.company, second.start_date, second.end_date) == (
        "Stage PFE", "Wevioo", date(2020, 6, 1), date(2020, 9, 1))


def test_education_range_gives_its_end_year_and_a_missing_school_is_flagged():
    cv = parse_cv_rules("Formation\nLicence en informatique (2015 - 2018)\n")
    assert [(e.institution, e.year) for e in cv.education] == [(NOT_SPECIFIED, 2018)]


def test_text_without_any_structure_gives_empty_lists_not_invented_data():
    cv = parse_cv_rules("Jean Dupont\nPassionné de cuisine et de randonnée.")
    assert (cv.skills, cv.experiences, cv.education, cv.languages) == ([], [], [], [])
