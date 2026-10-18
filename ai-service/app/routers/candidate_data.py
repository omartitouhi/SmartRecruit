import uuid

from fastapi import APIRouter, Depends, Response, status
from sqlalchemy.orm import Session

from app.core.database import get_session
from app.services.cv_store import delete_candidate_data

router = APIRouter(prefix="/candidates", tags=["RGPD"])


@router.delete("/{candidate_id}/data", status_code=status.HTTP_204_NO_CONTENT)
def erase_candidate_data(candidate_id: uuid.UUID, session: Session = Depends(get_session)):
    """Supprime toutes les données IA du candidat (BE-SEC-02, DATA_MODEL.md §4.6).

    Idempotent : 204 même si aucune donnée n'existe.
    """
    delete_candidate_data(session, candidate_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
