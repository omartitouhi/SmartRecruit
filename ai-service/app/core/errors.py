from datetime import datetime, timezone
from http import HTTPStatus

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.services.llm_client import LLMError, LLMNotConfiguredError


def error_response(request: Request, status_code: int, message: str) -> JSONResponse:
    """Corps d'erreur au format Spring Boot, commun à tous les services (DATA_MODEL.md §1) :
    {timestamp, status, error, message, path}."""
    return JSONResponse(
        status_code=status_code,
        content={
            "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "status": status_code,
            "error": HTTPStatus(status_code).phrase,
            "message": message,
            "path": request.url.path,
        },
    )


def _validation_message(exc: RequestValidationError) -> str:
    parts = []
    for error in exc.errors():
        location = ".".join(str(item) for item in error["loc"] if item not in ("body", "query", "path"))
        parts.append(f"{location} : {error['msg']}" if location else error["msg"])
    return "; ".join(parts) or "Requête invalide."


def register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(StarletteHTTPException)
    async def http_error(request: Request, exc: StarletteHTTPException):
        return error_response(request, exc.status_code, str(exc.detail))

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, exc: RequestValidationError):
        return error_response(request, status.HTTP_422_UNPROCESSABLE_ENTITY, _validation_message(exc))

    @app.exception_handler(LLMNotConfiguredError)
    async def llm_not_configured(request: Request, exc: LLMNotConfiguredError):
        return error_response(request, status.HTTP_503_SERVICE_UNAVAILABLE, str(exc))

    @app.exception_handler(LLMError)
    async def llm_failed(request: Request, exc: LLMError):
        return error_response(request, status.HTTP_502_BAD_GATEWAY, str(exc))
