from fastapi.testclient import TestClient
from sqlalchemy.exc import OperationalError

from app.core.database import get_session
from app.main import app

client = TestClient(app)


def test_ready_when_database_answers():
    response = client.get("/api/v1/ai/ready")
    assert response.status_code == 200
    assert response.json()["status"] == "READY"


def test_not_ready_when_database_is_down():
    class BrokenSession:
        def execute(self, *_):
            raise OperationalError("SELECT 1", {}, Exception("connection refused"))

    previous = app.dependency_overrides[get_session]
    app.dependency_overrides[get_session] = lambda: BrokenSession()
    try:
        response = client.get("/api/v1/ai/ready")
    finally:
        app.dependency_overrides[get_session] = previous
    assert response.status_code == 503
    assert response.json()["status"] == 503
