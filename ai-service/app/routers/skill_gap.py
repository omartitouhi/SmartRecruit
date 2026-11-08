import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.concurrency import run_in_threadpool
from sqlalchemy.orm import Session

from app.core.database import get_session
from app.schemas.common import SkillGapResult
from app.services.cv_store import find_parsed_cv
from app.services.job_analysis_service import find_job
from app.services.llm_client import OpenRouterClient, get_llm_client
from app.services.skill_gap_service import analyze_skill_gap

router = APIRouter(prefix="/skill-gap", tags=["IA-05 · Skill Gap"])


@router.get(
    "/candidates/{candidate_id}/jobs/{job_id}",
    response_model=SkillGapResult,
    responses={
        404: {"description": "CV ou offre jamais analysé(e)"},
        502: {"description": "Échec du LLM ou réponse non conforme au schéma"},
        503: {"description": "LLM non configuré"},
    },
)
async def skill_gap(
    candidate_id: uuid.UUID,
    job_id: uuid.UUID,
    llm: OpenRouterClient = Depends(get_llm_client),
    session: Session = Depends(get_session),
):
    """Compétences requises par l'offre cible absentes du profil du candidat (AI-REC-02).

    Le LLM tient compte des équivalences (Postgres = PostgreSQL) ; toute compétence
    renvoyée qui ne figure pas dans l'offre est rejetée.
    """
    cv = await run_in_threadpool(find_parsed_cv, session, candidate_id)
    if cv is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Aucun CV analysé pour ce candidat.")
    job = await run_in_threadpool(find_job, session, job_id)
    if job is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Offre non analysée : appeler d'abord /job-analysis/analyze.")
    return await analyze_skill_gap(cv, job, llm)
