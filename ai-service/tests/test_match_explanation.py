import asyncio
import math

import pytest

from app.schemas.common import MatchScore
from app.services.matching_service import (
    InsufficientDataError,
    MatchEvaluation,
    explain_score,
    score_match,
)
from tests.conftest import StubLLM
from tests.samples import CV, EVALUATION, job


def _evaluation(**overrides) -> MatchEvaluation:
    return MatchEvaluation.model_validate({**EVALUATION, **overrides})


def test_score_is_decomposed_into_explainable_criteria():
    result = explain_score(_evaluation())

    assert isinstance(result, MatchScore)
    assert result.score == 79.5  # 0.5 * 75 + 0.3 * 80 + 0.2 * 90
    assert [(c.label, c.weight, c.contribution) for c in result.criteria] == [
        ("Compétences techniques", 0.5, 37.5),
        ("Expérience", 0.3, 24.0),
        ("Formation", 0.2, 18.0),
    ]
    assert result.criteria[0].explanation == EVALUATION["skills"]["explanation"]


def test_contributions_add_up_to_the_global_score():
    result = explain_score(_evaluation(skills={"subScore": 66.6, "explanation": "Partiel."}))
    assert math.isclose(sum(c.contribution for c in result.criteria), result.score, abs_tol=0.1)


def test_serialisation_matches_the_match_score_contract():
    body = explain_score(_evaluation()).model_dump(by_alias=True)
    assert set(body) == {"score", "criteria"}
    assert set(body["criteria"][0]) == {"label", "weight", "contribution", "explanation"}


def test_criterion_without_data_gets_an_insufficient_data_explanation_not_a_score():
    result = explain_score(_evaluation(), {"education": "aucun diplôme n'a été extrait du CV."})

    education = result.criteria[2]
    assert (education.weight, education.contribution) == (0.0, 0.0)
    assert education.explanation == "Données insuffisantes : aucun diplôme n'a été extrait du CV."
    # (0.5 * 75 + 0.3 * 80) / 0.8 : le score repose uniquement sur les critères évaluables
    assert result.score == 76.9
    assert math.isclose(sum(c.weight for c in result.criteria), 1.0, abs_tol=1e-3)


def test_criterion_the_llm_could_not_evaluate_is_marked_insufficient():
    result = explain_score(_evaluation(experience={"subScore": None, "explanation": "Dates d'emploi absentes."}))
    assert result.criteria[1].explanation == "Données insuffisantes : Dates d'emploi absentes."
    assert result.criteria[1].contribution == 0.0


def test_no_evaluable_criterion_raises_instead_of_inventing_a_score():
    with pytest.raises(InsufficientDataError):
        explain_score(_evaluation(), {"skills": "a", "experience": "b", "education": "c"})


def test_score_match_flags_missing_cv_data_itself():
    cv = CV.model_copy(update={"education": []})
    llm = StubLLM().reply(EVALUATION)  # le LLM note la formation malgré tout : ignoré

    result = asyncio.run(score_match(cv, job(), llm))

    assert result.criteria[2].explanation.startswith("Données insuffisantes")
    assert result.criteria[2].contribution == 0.0


def test_score_match_does_not_call_the_llm_without_any_usable_data():
    empty_cv = CV.model_copy(update={"skills": [], "experiences": [], "education": []})
    llm = StubLLM()
    with pytest.raises(InsufficientDataError):
        asyncio.run(score_match(empty_cv, job(), llm))
    assert llm.calls == []


def test_job_without_required_skills_makes_the_skills_criterion_insufficient():
    llm = StubLLM().reply(EVALUATION)
    result = asyncio.run(score_match(CV, job(required_skills=()), llm))
    assert result.criteria[0].explanation == "Données insuffisantes : l'offre ne précise aucune compétence requise."
