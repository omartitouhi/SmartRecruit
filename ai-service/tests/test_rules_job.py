from app.schemas.enums import ExperienceLevel as L
from app.services.rules_job import analyze_job_rules


def test_skills_are_the_catalog_skills_cited_in_the_offer():
    job = analyze_job_rules("Nous cherchons un profil Java, Spring Boot, Postgres et Docker.", "Développeur Backend")
    assert job.required_skills == ["Java", "Spring Boot", "PostgreSQL", "Docker"]


def test_years_of_experience_set_the_level():
    for years, level in [(1, L.JUNIOR), (3, L.INTERMEDIATE), (6, L.SENIOR), (10, L.LEAD)]:
        assert analyze_job_rules(f"Développeur avec {years} ans d'expérience en Java.").experience_level == level


def test_the_lowest_number_of_years_wins_for_a_range():
    assert analyze_job_rules("Expérience de 3 à 5 ans sur Java requise.").experience_level == L.INTERMEDIATE


def test_keywords_decide_when_no_years_are_given_and_lead_marks_the_seniority():
    assert analyze_job_rules("Stage de fin d'études en Java, encadré par un tuteur.").experience_level == L.JUNIOR
    job = analyze_job_rules("Tech Lead : vous encadrez une équipe de 5 développeurs Java.", "Tech Lead")
    assert job.seniority == L.LEAD


def test_default_level_is_intermediate_when_nothing_is_stated():
    job = analyze_job_rules("Rejoignez-nous pour développer des applications Java.")
    assert (job.experience_level, job.seniority) == (L.INTERMEDIATE, L.INTERMEDIATE)
