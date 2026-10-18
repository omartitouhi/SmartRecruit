import uuid

from fastapi import APIRouter, Depends, Form, HTTPException, UploadFile, status
from fastapi.concurrency import run_in_threadpool
from sqlalchemy.orm import Session

from app.core.database import get_session
from app.schemas.common import CVParsingResult
from app.services.cv_parsing_service import (
    MAX_CV_SIZE_BYTES,
    CVTextExtractionError,
    extract_text_from_pdf,
)
from app.services.cv_store import file_fingerprint, find_cached_result, find_parsed_cv, save_parsed_cv
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
async def parse_cv(
    file: UploadFile,
    candidate_id: uuid.UUID | None = Form(None, alias="candidateId"),
    llm: OpenRouterClient = Depends(get_llm_client),
    session: Session = Depends(get_session),
):
    """PDF (multipart, champ `file`) -> compétences, expériences, diplômes, langues.

    Le texte est extrait (AI-CV-01) puis structuré par le LLM et validé contre le
    schéma strict CVParsingResult (AI-CV-02). Le délai du LLM (LLM_TIMEOUT_SECONDS,
    9 s par défaut) garantit une réponse sous 10 secondes.

    Avec le champ `candidateId`, le résultat est stocké dans ai_db et lié au candidat
    (AI-CV-04) ; un fichier identique déjà analysé est renvoyé sans nouvel appel au LLM.
    """
    data = await _read_pdf(file)
    fingerprint = file_fingerprint(data)
    if candidate_id:
        cached = await run_in_threadpool(find_cached_result, session, candidate_id, fingerprint)
        if cached:
            return cached

    try:
        text = await run_in_threadpool(extract_text_from_pdf, data)
    except CVTextExtractionError as exc:
        raise _invalid_file(str(exc)) from exc

    result = await parse_cv_text(text, llm)
    if candidate_id:
        await run_in_threadpool(save_parsed_cv, session, candidate_id, fingerprint, result)
    return result


@router.get("/candidates/{candidate_id}", response_model=CVParsingResult)
def get_parsed_cv(candidate_id: uuid.UUID, session: Session = Depends(get_session)):
    """Dernier résultat de parsing stocké pour le candidat (AI-CV-04), 404 si aucun."""
    result = find_parsed_cv(session, candidate_id)
    if result is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Aucun CV analysé pour ce candidat.",
        )
    return result
