from fastapi import APIRouter, HTTPException, status

router = APIRouter(prefix="/recommendation", tags=["IA-04 · Recommandation"])


@router.get("/candidates/{candidate_id}/jobs")
async def recommend_jobs(candidate_id: str):
    """Offres pertinentes personnalisées pour un candidat (IA-04)."""
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail="Recommendation not implemented yet — see Sprint 4-5 (Matching, score, recommandations).",
    )
