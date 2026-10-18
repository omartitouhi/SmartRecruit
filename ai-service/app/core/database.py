from collections.abc import Iterator
from functools import lru_cache

from sqlalchemy import Engine, create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.core.config import settings


class Base(DeclarativeBase):
    pass


@lru_cache
def get_engine() -> Engine:
    """Moteur `ai_db`, créé au premier usage (aucune connexion à l'import)."""
    return create_engine(settings.database_url, pool_pre_ping=True)


def init_db(engine: Engine | None = None) -> None:
    """Crée les tables manquantes (pas de migrations : schéma interne au service)."""
    from app import models  # noqa: F401 — enregistre les tables sur Base.metadata

    Base.metadata.create_all(engine or get_engine())


def get_session() -> Iterator[Session]:
    """Dépendance FastAPI (surchargée dans les tests)."""
    session = sessionmaker(bind=get_engine(), expire_on_commit=False)()
    try:
        yield session
    finally:
        session.close()
