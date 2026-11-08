import hashlib
import uuid
from datetime import datetime, timezone

from sqlalchemy.orm import Session

from app.models import ParsedCV
from app.schemas.common import CVParsingResult


def file_fingerprint(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def find_parsed_cv(session: Session, candidate_id: uuid.UUID) -> CVParsingResult | None:
    stored = session.get(ParsedCV, candidate_id)
    return CVParsingResult.model_validate(stored.result) if stored else None


def find_cached_result(session: Session, candidate_id: uuid.UUID, fingerprint: str) -> CVParsingResult | None:
    """Résultat déjà stocké pour ce candidat ET ce fichier exact, sinon None."""
    stored = session.get(ParsedCV, candidate_id)
    if stored is None or stored.file_sha256 != fingerprint:
        return None
    return CVParsingResult.model_validate(stored.result)


def save_parsed_cv(
    session: Session, candidate_id: uuid.UUID, fingerprint: str, result: CVParsingResult
) -> None:
    """Enregistre (ou remplace) le CV courant du candidat."""
    session.merge(ParsedCV(
        candidate_id=candidate_id,
        file_sha256=fingerprint,
        result=result.model_dump(mode="json", by_alias=True),
        parsed_at=datetime.now(timezone.utc),
    ))
    session.commit()


def delete_candidate_data(session: Session, candidate_id: uuid.UUID) -> None:
    """Effacement RGPD (DATA_MODEL.md §4.6) — idempotent."""
    session.query(ParsedCV).filter(ParsedCV.candidate_id == candidate_id).delete()
    session.commit()
