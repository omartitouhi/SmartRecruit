from fastapi import APIRouter, Depends, HTTPException, UploadFile, status
from fastapi.concurrency import run_in_threadpool

from app.schemas.common import CVParsingResult
from app.services.cv_parsing_service import (
    MAX_CV_SIZE_BYTES,
    CVTextExtractionError,
    extract_text_from_pdf,
)
from app.services.cv_structuring_service import parse_cv_text
from app.services.llm_client import OpenRouterClient, get_llm_client

router = APIRouter(prefix="/cv-parsing", tags=["IA-01 · CV Parsing"])

PDF_MAGIC = b"%PDF-"


def _invalid_file(message: str) -> HTTPException:
    return HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=message)


async def _read_pdf(file: UploadFile) -> bytes:
    """Valide le fichier reçu (DATA_MODEL.md §1 : PDF uniquement, 5 Mo max) ; tout
    fichier invalide donne un 422 avec le détail de l'erreur (AI-CV-03)."""
    if file.content_type != "application/pdf":
        raise _invalid_file("Seuls les fichiers PDF (application/pdf) sont acceptés.")

    data = await file.read(MAX_CV_SIZE_BYTES + 1)
    if not data:
        raise _invalid_file("Le fichier est vide.")
    if len(data) > MAX_CV_SIZE_BYTES:
        raise _invalid_file("Le fichier dépasse la taille maximale autorisée (5 Mo).")
    if not data.startswith(PDF_MAGIC):
        raise _invalid_file("Le fichier n'est pas un PDF valide.")
    return data


@router.post(
    "/parse",
    response_model=CVParsingResult,
    responses={
        422: {"description": "Fichier invalide (type, taille, PDF corrompu, scanné sans texte)"},
        502: {"description": "Échec du LLM ou réponse non conforme au schéma"},
        503: {"description": "LLM non configuré"},
    },
)
async def parse_cv(file: UploadFile, llm: OpenRouterClient = Depends(get_llm_client)):
    """PDF (multipart, champ `file`) -> compétences, expériences, diplômes, langues.

    Le texte est extrait (AI-CV-01) puis structuré par le LLM et validé contre le
    schéma strict CVParsingResult (AI-CV-02). Le délai du LLM (LLM_TIMEOUT_SECONDS,
    9 s par défaut) garantit une réponse sous 10 secondes.
    """
    data = await _read_pdf(file)
    try:
        text = await run_in_threadpool(extract_text_from_pdf, data)
    except CVTextExtractionError as exc:
        raise _invalid_file(str(exc)) from exc

    return await parse_cv_text(text, llm)
