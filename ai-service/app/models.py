import uuid
from datetime import datetime

from sqlalchemy import JSON, DateTime, String, Text, Uuid
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base

_JSON = JSON().with_variant(JSONB(), "postgresql")


class ParsedCV(Base):
    """Résultat du parsing du CV d'un candidat (AI-CV-04) — un seul CV courant par candidat.

    Supprimé avec toutes les données du candidat sur demande RGPD (DATA_MODEL.md §4.6).
    """

    __tablename__ = "parsed_cvs"

    candidate_id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    # SHA-256 du PDF : un CV identique déjà analysé n'est pas renvoyé au LLM.
    file_sha256: Mapped[str] = mapped_column(String(64))
    result: Mapped[dict] = mapped_column(_JSON)
    parsed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class JobAnalysis(Base):
    """Analyse LLM d'une offre (AI-JOB-01), réutilisée par le matching, la
    recommandation et le skill gap. Remplacée à chaque nouvelle analyse de l'offre."""

    __tablename__ = "job_analyses"

    job_id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    title: Mapped[str | None] = mapped_column(String(255), nullable=True)
    description: Mapped[str] = mapped_column(Text)
    result: Mapped[dict] = mapped_column(_JSON)
    analyzed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
