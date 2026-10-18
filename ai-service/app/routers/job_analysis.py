import uuid

from fastapi import APIRouter, Depends
from fastapi.concurrency import run_in_threadpool
from pydantic import Field
from sqlalchemy.orm import Session

from app.core.database import get_session
from app.schemas.common import CamelModel, JobAnalysisResult
from app.services.job_analysis_service import analyze_job_description, save_job_analysis
from app.services.llm_client import OpenRouterClient, get_llm_client

router = APIRouter(prefix="/job-analysis", tags=["IA-02 · Job Analysis"])


class JobAnalysisRequest(CamelModel):
    job_id: uuid.UUID
    title: str | None = Field(None, max_length=255)
    description: str = Field(min_length=20, max_length=20_000)


@router.post(
    "/analyze",
    response_model=JobAnalysisResult,
    responses={
        502: {"description": "Échec du LLM ou réponse non conforme au schéma"},
        503: {"description": "LLM non configuré"},
    },
)
async def analyze_job(
    payload: JobAnalysisRequest,
    llm: OpenRouterClient = Depends(get_llm_client),
    session: Session = Depends(get_session),
):
    """Offre -> compétences requises, expérience, niveau, extraits par le LLM (AI-JOB-01).

    Le résultat est stocké pour le matching, la recommandation et le skill gap ;
    une nouvelle analyse de la même offre remplace la précédente.
    """
    result = await analyze_job_description(payload.description, llm, payload.title)
    await run_in_threadpool(
        save_job_analysis, session, payload.job_id, payload.title, payload.description, result
    )
    return result
