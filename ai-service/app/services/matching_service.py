import json
from datetime import date

from pydantic import ConfigDict, Field

from app.core.config import settings
from app.schemas.common import CamelModel, CVParsingResult
from app.services.job_analysis_service import AnalyzedJob
from app.services.llm_client import OpenRouterClient

MAX_JOB_DESCRIPTION_CHARS = 3_000

SYSTEM_PROMPT = """Tu évalues l'adéquation d'un CV à une offre d'emploi, critère par critère, pour aider
un recruteur (tu ne prends aucune décision de recrutement). Tu réponds UNIQUEMENT par un objet JSON :
{"skills": {"subScore": number, "explanation": string},
 "experience": {"subScore": number, "explanation": string},
 "education": {"subScore": number, "explanation": string}}
- subScore : entre 0 et 100.
- skills : compétences requises couvertes par le CV, en tenant compte des équivalences
  (ex. Postgres = PostgreSQL) et des technologies proches.
- experience : durée et pertinence de l'expérience au regard du niveau attendu
  (JUNIOR 0-2 ans, INTERMEDIATE 2-5 ans, SENIOR 5-8 ans, LEAD 8 ans et +).
- education : adéquation des diplômes au poste.
- explanation : une ou deux phrases factuelles en français, citant les éléments du CV et de l'offre.
  N'invente aucun élément absent des données fournies."""


class _Strict(CamelModel):
    model_config = ConfigDict(extra="forbid")


class CriterionEvaluation(_Strict):
    sub_score: float = Field(ge=0, le=100)
    explanation: str = Field(min_length=1)


class MatchEvaluation(_Strict):
    """Sortie du LLM pour AI-MATCH-01, validée par schéma strict."""

    skills: CriterionEvaluation
    experience: CriterionEvaluation
    education: CriterionEvaluation


def _prompt(cv: CVParsingResult, job: AnalyzedJob) -> str:
    offer = {
        "title": job.title,
        **job.analysis.model_dump(mode="json", by_alias=True),
        "description": job.description[:MAX_JOB_DESCRIPTION_CHARS],
    }
    return (
        f"Date du jour : {date.today().isoformat()}\n\n"
        f"CV (JSON) :\n{cv.model_dump_json(by_alias=True)}\n\n"
        f"Offre (JSON) :\n{json.dumps(offer, ensure_ascii=False)}"
    )


async def evaluate_match(cv: CVParsingResult, job: AnalyzedJob, llm: OpenRouterClient) -> MatchEvaluation:
    """Évaluation sémantique CV/offre par le LLM, critère par critère (AI-MATCH-01).

    Une sortie non conforme (sous-score hors 0-100, critère manquant, champ inconnu)
    ou un échec OpenRouter lève LLMError (502) / LLMNotConfiguredError (503).
    """
    return await llm.complete_json(
        SYSTEM_PROMPT, _prompt(cv, job), MatchEvaluation, timeout_seconds=settings.matching_timeout_seconds
    )
