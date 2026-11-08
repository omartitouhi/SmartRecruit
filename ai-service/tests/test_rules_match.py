import asyncio
from datetime import date

import pytest

from app.core.config import settings
from app.schemas.common import CVParsingResult, ParsedEducation, ParsedExperience
from app.schemas.enums import ExperienceLevel as L
from app.services.matching_service import InsufficientDataError, evaluate_match, missing_data, score_match
from app.services.recommendation_service import recommend_jobs
from app.services.rules_match import experience_years, skill_gap_rules
from app.services.skill_gap_service import analyze_skill_gap
from tests.samples import CV, job


@pytest.fixture(autouse=True)
def rules_engine(monkeypatch):
    monkeypatch.setattr(settings, "ai_engine", "rules")


def _cv(**changes):
    return CV.model_copy(update=changes)


def test_score_is_explained_criterion_by_criterion_without_any_llm():
    score = asyncio.run(score_match(CV, job(), llm=None))
    labels = {c.label: c for c in score.criteria}
    assert "3 compétences requises sur 4 présentes" in labels["Compétences techniques"].explanation
    assert "manque : Kubernetes" in labels["Compétences techniques"].explanation
    assert sum(c.weight for c in score.criteria) == pytest.approx(1)
    assert sum(c.contribution for c in score.criteria) == pytest.approx(score.score, abs=0.2)


def test_explanations_use_french_plurals_and_level_names():
    one = asyncio.run(evaluate_match(CV, job(required_skills=("Java", "Terraform"), level=L.JUNIOR), llm=None))
    assert one.skills.explanation == "1 compétence requise sur 2 présente dans le CV (Java) ; manque : Terraform."
    assert "niveau junior (aucune expérience exigée)" in one.experience.explanation
    none = asyncio.run(evaluate_match(CV, job(required_skills=("PHP",), level=L.SENIOR), llm=None))
    assert none.skills.explanation.startswith("La compétence requise n'est pas présente")


def test_alias_in_the_cv_counts_as_the_required_skill():
    score = asyncio.run(score_match(CV, job(required_skills=("PostgreSQL",)), llm=None))
    assert score.criteria[0].contribution == 50.0  # 100 % des compétences, poids 0,5


def test_skill_cited_in_an_experience_description_is_owned():
    cv = _cv(skills=["Java"])
    assert skill_gap_rules(cv, job(required_skills=("Spring Boot",))) == []


def test_experience_years_merge_overlapping_periods():
    cv = _cv(experiences=[
        ParsedExperience(title="A", company="X", start_date=date(2020, 1, 1), end_date=date(2022, 1, 1), description=""),
        ParsedExperience(title="B", company="Y", start_date=date(2021, 1, 1), end_date=date(2023, 1, 1), description=""),
    ])
    assert experience_years(cv, today=date(2030, 1, 1)) == 3


def test_experience_below_the_expected_level_is_pro_rated():
    cv = _cv(experiences=[ParsedExperience(
        title="Dev", company="X", start_date=date(2024, 1, 1), end_date=date(2025, 1, 1), description="")])
    evaluation = asyncio.run(evaluate_match(cv, job(level=L.SENIOR), llm=None))
    assert evaluation.experience.sub_score == 20.0  # 1 an sur 5 attendus


def test_unrecognised_degree_is_reported_as_insufficient_data():
    cv = _cv(education=[ParsedEducation(degree="Formation interne", institution="X", year=2020)])
    score = asyncio.run(score_match(cv, job(), llm=None))
    education = next(c for c in score.criteria if c.label == "Formation")
    assert education.weight == 0 and education.explanation.startswith("Données insuffisantes")


def test_cv_without_data_raises_insufficient_data_before_any_scoring():
    empty = CVParsingResult(skills=[], experiences=[], education=[], languages=[])
    assert len(missing_data(empty, job())) == 3
    with pytest.raises(InsufficientDataError):
        asyncio.run(score_match(empty, job(), llm=None))


def test_skill_gap_lists_missing_required_skills_in_the_offer_spelling():
    result = asyncio.run(analyze_skill_gap(
        CV, job(required_skills=("Java", "PostgreSQL", "Kubernetes", "Terraform")), llm=None))
    assert result.missing_skills == ["Kubernetes", "Terraform"]


def test_recommendations_rank_by_score_and_skip_unrelated_offers():
    close = job()
    far = job(required_skills=("Java", "Kubernetes", "Terraform", "AWS"))
    unrelated = job(required_skills=("PHP",))
    result = asyncio.run(recommend_jobs(CV, [far, unrelated, close], llm=None))
    assert [r.job_id for r in result.recommendations] == [close.job_id, far.job_id]
    assert result.recommendations[0].score > result.recommendations[1].score
