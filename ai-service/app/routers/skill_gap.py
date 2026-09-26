from fastapi import APIRouter, HTTPException, status

from app.schemas.common import SkillGapResult

router = APIRouter(prefix="/skill-gap", tags=["IA-05 · Skill Gap"])


@router.get("/candidates/{candidate_id}/jobs/{job_id}", response_model=SkillGapResult)
async def skill_gap(candidate_id: str, job_id: str):
    """Compétences manquantes pour une offre cible (IA-05)."""
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail="Skill gap analysis not implemented yet — see Sprint 4-5 (Matching, score, recommandations).",
    )
