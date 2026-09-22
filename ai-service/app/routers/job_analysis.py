from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel

router = APIRouter(prefix="/job-analysis", tags=["IA-02 · Job Analysis"])


class JobAnalysisRequest(BaseModel):
    job_id: str
    description: str


@router.post("/analyze")
async def analyze_job(payload: JobAnalysisRequest):
    """Offre -> compétences requises, expérience, niveau (IA-02)."""
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail="Job analysis not implemented yet — see Sprint 4-5 (Matching, score, recommandations).",
    )
