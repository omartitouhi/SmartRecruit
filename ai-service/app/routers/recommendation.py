import uuid

from fastapi import APIRouter, Depends
from fastapi.concurrency import run_in_threadpool
from sqlalchemy.orm import Session

from app.core.database import get_session
from app.schemas.common import RecommendationResult
from app.services.cv_store import find_parsed_cv
from app.services.job_analysis_service import list_jobs
from app.services.llm_client import OpenRouterClient, get_llm_client
from app.services.recommendation_service import recommend_jobs

router = APIRouter(prefix="/recommendation", tags=["IA-04 · Recommandation"])


@router.get(
    "/candidates/{candidate_id}/jobs",
    response_model=RecommendationResult,
    responses={
        502: {"description": "Échec du LLM ou réponse non conforme au schéma"},
        503: {"description": "LLM non configuré"},
    },
)
async def recommend_jobs_for_candidate(
    candidate_id: uuid.UUID,
    llm: OpenRouterClient = Depends(get_llm_client),
    session: Session = Depends(get_session),
):
    """Offres pertinentes classées par le LLM pour un candidat (AI-REC-01), 10 au plus.

    Contrat DATA_MODEL.md §4.7 (consommé par job-service, BE-REC-01). Candidat sans
    CV analysé ou aucune offre analysée : liste vide. Les offres connues sont celles
    passées par /job-analysis/analyze ; job-service ne garde que les offres PUBLISHED.
    """
    cv = await run_in_threadpool(find_parsed_cv, session, candidate_id)
    if cv is None:
        return RecommendationResult(recommendations=[])
    jobs = await run_in_threadpool(list_jobs, session)
    return await recommend_jobs(cv, jobs, llm)
