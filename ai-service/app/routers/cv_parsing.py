from fastapi import APIRouter, HTTPException, UploadFile, status

from app.schemas.common import CVParsingResult
from app.services.cv_parsing_service import (
    MAX_CV_SIZE_BYTES,
    CVTextExtractionError,
    extract_text_from_pdf,
)
from app.services.cv_structuring_service import parse_cv_text

router = APIRouter(prefix="/cv-parsing", tags=["IA-01 · CV Parsing"])


@router.post("/parse", response_model=CVParsingResult)
async def parse_cv(file: UploadFile):
    """PDF -> compétences, expériences, diplômes, langues (AI-CV-01 + AI-CV-02).

    Le fichier est validé (type, taille), son texte extrait (erreur 422 explicite
    en cas d'échec), puis structuré et validé contre le schéma strict CVParsingResult.
    """
    if file.content_type != "application/pdf":
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="Seuls les fichiers PDF (application/pdf) sont acceptés.",
        )

    data = await file.read()
    if len(data) > MAX_CV_SIZE_BYTES:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="Le fichier dépasse la taille maximale autorisée (5 Mo).",
        )

    try:
        text = extract_text_from_pdf(data)
    except CVTextExtractionError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(exc),
        ) from exc

    return parse_cv_text(text)
