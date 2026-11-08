import asyncio
import json

import pytest

from app.services.llm_client import LLMError, LLMNotConfiguredError
from app.services.matching_service import MatchEvaluation, evaluate_match
from tests.conftest import StubLLM
from tests.samples import CV, EVALUATION, job


def _evaluate(llm: StubLLM, offer=None) -> MatchEvaluation:
    return asyncio.run(evaluate_match(CV, offer or job(), llm))


def test_each_criterion_gets_a_sub_score_and_a_justification():
    evaluation = _evaluate(StubLLM().reply(EVALUATION))
    assert (evaluation.skills.sub_score, evaluation.experience.sub_score, evaluation.education.sub_score) == (75, 80, 90)
    assert "Kubernetes" in evaluation.skills.explanation


def test_structured_cv_and_job_are_sent_to_the_llm():
    llm = StubLLM().reply(EVALUATION)
    _evaluate(llm, job(title="Data Engineer"))
    prompt = llm.calls[0]["user"]
    assert '"skills":["Java","Spring Boot","Postgres","Docker"]' in prompt
    assert '"requiredSkills": ["Java", "Spring Boot", "PostgreSQL", "Kubernetes"]' in prompt
    assert "Data Engineer" in prompt and "Date du jour" in prompt


@pytest.mark.parametrize(
    "invalid",
    [
        {**EVALUATION, "skills": {"subScore": 120, "explanation": "Excellent."}},
        {**EVALUATION, "experience": {"subScore": -5, "explanation": "Aucune."}},
        {**EVALUATION, "education": {"subScore": 50, "explanation": ""}},
        {"skills": EVALUATION["skills"], "experience": EVALUATION["experience"]},
        {**EVALUATION, "globalScore": 82},
        {**EVALUATION, "skills": {"subScore": "élevé", "explanation": "Bien."}},
    ],
)
def test_non_conforming_output_is_rejected_rather_than_scored(invalid):
    with pytest.raises(LLMError):
        _evaluate(StubLLM().reply(json.dumps(invalid)))


def test_openrouter_errors_are_propagated():
    llm = StubLLM()
    llm.error = LLMNotConfiguredError("Service LLM non configuré")
    with pytest.raises(LLMNotConfiguredError):
        _evaluate(llm)
