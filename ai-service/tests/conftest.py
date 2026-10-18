import json

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.core.database import get_session, init_db
from app.main import app
from app.services.llm_client import get_llm_client, parse_llm_json


class StubLLM:
    """Remplace OpenRouter : renvoie des réponses JSON préparées, validées par le même
    chemin que le vrai client (`parse_llm_json`), et garde la trace des prompts reçus."""

    def __init__(self):
        self.responses: list = []
        self.error: Exception | None = None
        self.calls: list[dict] = []

    def reply(self, *responses):
        self.responses.extend(responses)
        return self

    async def complete_json(self, system, user, schema, timeout_seconds=None):
        self.calls.append({"system": system, "user": user, "schema": schema})
        if self.error:
            raise self.error
        response = self.responses.pop(0)
        return parse_llm_json(response if isinstance(response, str) else json.dumps(response), schema)


@pytest.fixture
def stub_llm():
    stub = StubLLM()
    app.dependency_overrides[get_llm_client] = lambda: stub
    yield stub
    app.dependency_overrides.pop(get_llm_client, None)


@pytest.fixture(autouse=True)
def db_session():
    """Base SQLite en mémoire à la place de PostgreSQL (ai_db), recréée à chaque test."""
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    init_db(engine)
    session = sessionmaker(bind=engine, expire_on_commit=False)()
    app.dependency_overrides[get_session] = lambda: session
    yield session
    app.dependency_overrides.pop(get_session, None)
    session.close()
    engine.dispose()
