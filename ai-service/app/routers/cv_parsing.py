from fastapi import APIRouter, HTTPException, UploadFile, status

from app.schemas.common import CVParsingResult

router = APIRouter(prefix="/cv-parsing", tags=["IA-01 · CV Parsing"])


@router.post("/parse", response_model=CVParsingResult)
async def parse_cv(file: UploadFile):
    """PDF -> compétences, expériences, diplômes, langues (IA-01)."""
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail="CV parsing not implemented yet — see Sprint 4-5 (Matching, score, recommandations).",
    )
