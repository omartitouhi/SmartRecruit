import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.concurrency import run_in_threadpool
from sqlalchemy.orm import Session

from app.core.database import get_session
from app.schemas.common import CamelModel, MatchScore
from app.services.cv_store import find_parsed_cv
from app.services.job_analysis_service import find_job
from app.services.llm_client import OpenRouterClient, get_llm_client
from app.services.matching_service import InsufficientDataError, score_match

router = APIRouter(prefix="/matching", tags=["IA-03 · Matching"])


class MatchRequest(CamelModel):
    # Identifiant du candidat dont le CV a été analysé et stocké (AI-CV-04).
    cv_id: uuid.UUID
    job_id: uuid.UUID


@router.post(
    "/score",
    response_model=MatchScore,
    responses={
        404: {"description": "CV ou offre jamais analysé(e)"},
        422: {"description": "Données insuffisantes pour évaluer un seul critère"},
        502: {"description": "Échec du LLM ou réponse non conforme au schéma"},
        503: {"description": "LLM non configuré"},
    },
)
async def compute_match_score(
    payload: MatchRequest,
    llm: OpenRouterClient = Depends(get_llm_client),
    session: Session = Depends(get_session),
):
    """Score de compatibilité CV/offre toujours accompagné d'une explication par critère (IA-03).

    Le LLM évalue chaque critère (AI-MATCH-01), le code calcule le score pondéré
    (AI-MATCH-02) et sa décomposition (AI-MATCH-03). Réponse sous 15 s
    (MATCHING_TIMEOUT_SECONDS). Aucune décision de recrutement n'est automatisée :
    le score est un outil d'aide à la décision.
    """
    cv = await run_in_threadpool(find_parsed_cv, session, payload.cv_id)
    if cv is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Aucun CV analysé pour ce candidat.")
    job = await run_in_threadpool(find_job, session, payload.job_id)
    if job is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Offre non analysée : appeler d'abord /job-analysis/analyze.")

    try:
        return await score_match(cv, job, llm)
    except InsufficientDataError as exc:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, str(exc)) from exc
