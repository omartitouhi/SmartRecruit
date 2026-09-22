from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel

from app.schemas.common import MatchScore

router = APIRouter(prefix="/matching", tags=["IA-03 · Matching"])


class MatchRequest(BaseModel):
    cv_id: str
    job_id: str


@router.post("/score", response_model=MatchScore)
async def compute_match_score(payload: MatchRequest):
    """Embeddings + similarité sémantique -> score toujours accompagné d'une explication (IA-03).

    Chaque score DOIT être décomposable en critères explicables (voir slide 7 : Explicabilité).
    Aucune décision de recrutement n'est automatisée — le score est un outil d'aide à la décision.
    """
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail="Matching not implemented yet — see Sprint 4-5 (Matching, score, recommandations).",
    )
