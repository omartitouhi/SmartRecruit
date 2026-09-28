from fastapi import APIRouter, HTTPException, UploadFile, status

from app.schemas.common import CVParsingResult
from app.services.cv_parsing_service import (
    MAX_CV_SIZE_BYTES,
    CVTextExtractionError,
    extract_text_from_pdf,
)

router = APIRouter(prefix="/cv-parsing", tags=["IA-01 · CV Parsing"])


@router.post("/parse", response_model=CVParsingResult)
async def parse_cv(file: UploadFile):
    """PDF -> compétences, expériences, diplômes, langues (IA-01/IA-02).

    IA-01 (extraction de texte brut) est implémentée : le fichier est validé
    (type, taille) puis son texte est extrait, avec une erreur explicite en cas
    d'échec (PDF corrompu, protégé, ou sans couche de texte — ex. un scan/image).
    L'extraction structurée (compétences/expériences/diplômes) arrive avec IA-02.
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
        extract_text_from_pdf(data)
    except CVTextExtractionError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(exc),
        ) from exc

    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail=(
            "Extraction de texte OK (AI-CV-01) — l'extraction structurée "
            "(compétences, expériences, diplômes, langues) arrive avec AI-CV-02."
        ),
    )
