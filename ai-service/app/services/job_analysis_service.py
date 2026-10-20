import uuid
from dataclasses import dataclass
from datetime import datetime, timezone

from pydantic import ConfigDict
from sqlalchemy.orm import Session

from app.core.config import settings
from app.models import JobAnalysis
from app.schemas.common import JobAnalysisResult
from app.services.grounding import appears_in, dedupe
from app.services.llm_client import LLMError, OpenRouterClient
from app.services.rules_job import analyze_job_rules

MAX_JOB_TEXT_CHARS = 12_000

SYSTEM_PROMPT = """Tu analyses des offres d'emploi. Tu réponds UNIQUEMENT par un objet JSON :
{"requiredSkills": [string], "experienceLevel": "JUNIOR"|"INTERMEDIATE"|"SENIOR"|"LEAD",
"seniority": "JUNIOR"|"INTERMEDIATE"|"SENIOR"|"LEAD"}
- requiredSkills : compétences techniques et outils demandés, recopiés tels qu'écrits dans l'offre.
  N'ajoute aucune compétence qui n'y figure pas.
- experienceLevel : expérience attendue (JUNIOR 0-2 ans, INTERMEDIATE 2-5 ans, SENIOR 5-8 ans, LEAD 8 ans et +).
- seniority : niveau de responsabilité du poste (LEAD = encadrement d'équipe ou rôle de référent).
- Si l'offre ne précise rien, déduis le niveau des missions décrites."""


class _StrictJobAnalysis(JobAnalysisResult):
    model_config = ConfigDict(extra="forbid")


@dataclass
class AnalyzedJob:
    """Offre analysée telle qu'utilisée par le matching, la recommandation et le skill gap."""

    job_id: uuid.UUID
    title: str | None
    description: str
    analysis: JobAnalysisResult


async def analyze_job_description(
    description: str, llm: OpenRouterClient, title: str | None = None
) -> JobAnalysisResult:
    """Offre en texte libre -> JobAnalysisResult produit par le LLM (AI-JOB-01).

    Sortie validée par schéma strict ; les compétences absentes du texte de l'offre
    sont écartées. LLMError / LLMNotConfiguredError sinon : rien n'est inventé.
    """
    if settings.ai_engine == "rules":
        return analyze_job_rules(description, title)
    offer = f"Titre : {title}\n" if title else ""
    offer += f"Description :\n\"\"\"\n{description[:MAX_JOB_TEXT_CHARS]}\n\"\"\""
    output = await llm.complete_json(SYSTEM_PROMPT, offer, _StrictJobAnalysis)
    source = f"{title or ''}\n{description}"
    skills = dedupe([s for s in output.required_skills if appears_in(s, source)])
    if output.required_skills and not skills:
        raise LLMError("Aucune des compétences renvoyées par le LLM ne figure dans l'offre.")
    return JobAnalysisResult(
        required_skills=skills,
        experience_level=output.experience_level,
        seniority=output.seniority,
    )


def save_job_analysis(
    session: Session, job_id: uuid.UUID, title: str | None, description: str, result: JobAnalysisResult
) -> None:
    session.merge(JobAnalysis(
        job_id=job_id,
        title=title,
        description=description,
        result=result.model_dump(mode="json", by_alias=True),
        analyzed_at=datetime.now(timezone.utc),
    ))
    session.commit()


def _to_analyzed_job(stored: JobAnalysis) -> AnalyzedJob:
    return AnalyzedJob(
        job_id=stored.job_id,
        title=stored.title,
        description=stored.description,
        analysis=JobAnalysisResult.model_validate(stored.result),
    )


def find_job(session: Session, job_id: uuid.UUID) -> AnalyzedJob | None:
    stored = session.get(JobAnalysis, job_id)
    return _to_analyzed_job(stored) if stored else None


def list_jobs(session: Session) -> list[AnalyzedJob]:
    return [_to_analyzed_job(job) for job in session.query(JobAnalysis).order_by(JobAnalysis.analyzed_at.desc())]
