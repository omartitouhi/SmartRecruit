import json
import logging

from pydantic import BaseModel, ConfigDict, Field

from app.core.config import settings
from app.schemas.common import CVParsingResult, Recommendation, RecommendationResult
from app.services.grounding import normalize
from app.services.job_analysis_service import AnalyzedJob
from app.services.llm_client import OpenRouterClient
from app.services.rules_match import recommend_rules

log = logging.getLogger(__name__)

MAX_RECOMMENDATIONS = 10
# Offres soumises au LLM au maximum, présélectionnées par recouvrement de compétences
# pour borner la taille du prompt (et donc la latence et le coût).
MAX_CANDIDATE_JOBS = 40

SYSTEM_PROMPT = f"""Tu recommandes des offres d'emploi à un candidat. Tu réponds UNIQUEMENT par un objet JSON :
{{"recommendations": [{{"jobId": string, "score": number}}]}}
- Classe les offres de la plus à la moins pertinente pour le profil (compétences, en tenant compte
  des équivalences et technologies proches, expérience, formation). Au plus {MAX_RECOMMENDATIONS} offres.
- jobId : recopie exactement un identifiant de la liste fournie ; n'en invente aucun.
- score : pertinence entre 0 et 100. N'inclus pas les offres sans rapport avec le profil."""


class _LLMRecommendation(BaseModel):
    model_config = ConfigDict(extra="forbid")

    jobId: str
    score: float = Field(ge=0, le=100)


class LLMRecommendations(BaseModel):
    model_config = ConfigDict(extra="forbid")

    recommendations: list[_LLMRecommendation]


def _skill_overlap(cv: CVParsingResult, job: AnalyzedJob) -> int:
    cv_skills = {normalize(skill) for skill in cv.skills}
    return sum(normalize(skill) in cv_skills for skill in job.analysis.required_skills)


def shortlist(cv: CVParsingResult, jobs: list[AnalyzedJob]) -> list[AnalyzedJob]:
    """Présélection déterministe : recouvrement de compétences décroissant (tri stable,
    les offres les plus récemment analysées d'abord à égalité)."""
    return sorted(jobs, key=lambda job: _skill_overlap(cv, job), reverse=True)[:MAX_CANDIDATE_JOBS]


def _prompt(cv: CVParsingResult, jobs: list[AnalyzedJob]) -> str:
    profile = {
        "skills": cv.skills,
        "experiences": [
            {"title": e.title, "startDate": e.start_date.isoformat(),
             "endDate": e.end_date.isoformat() if e.end_date else None}
            for e in cv.experiences
        ],
        "education": [{"degree": e.degree, "year": e.year} for e in cv.education],
    }
    offers = [
        {"jobId": str(job.job_id), "title": job.title, **job.analysis.model_dump(mode="json", by_alias=True)}
        for job in jobs
    ]
    return (
        f"Profil du candidat (JSON) :\n{json.dumps(profile, ensure_ascii=False)}\n\n"
        f"Offres publiées (JSON) :\n{json.dumps(offers, ensure_ascii=False)}"
    )


def keep_known_jobs(output: LLMRecommendations, jobs: list[AnalyzedJob]) -> list[Recommendation]:
    """Anti-hallucination : tout identifiant absent des offres fournies est rejeté ;
    doublons écartés, tri par score décroissant, 10 offres au plus."""
    known = {str(job.job_id).lower(): job.job_id for job in jobs}
    best: dict = {}
    for item in output.recommendations:
        job_id = known.get(item.jobId.strip().lower())
        if job_id is None:
            log.warning("Recommandation rejetée : offre inconnue %r renvoyée par le LLM", item.jobId)
            continue
        if job_id not in best:
            best[job_id] = item.score
    ranked = sorted(best.items(), key=lambda pair: pair[1], reverse=True)
    return [Recommendation(job_id=job_id, score=score) for job_id, score in ranked[:MAX_RECOMMENDATIONS]]


async def recommend_jobs(
    cv: CVParsingResult, jobs: list[AnalyzedJob], llm: OpenRouterClient
) -> RecommendationResult:
    """Offres classées par le LLM pour un candidat (AI-REC-01)."""
    if not jobs:
        return RecommendationResult(recommendations=[])
    if settings.ai_engine == "rules":
        return recommend_rules(cv, jobs)
    candidates = shortlist(cv, jobs)
    output = await llm.complete_json(SYSTEM_PROMPT, _prompt(cv, candidates), LLMRecommendations)
    return RecommendationResult(recommendations=keep_known_jobs(output, candidates))
