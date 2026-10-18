from datetime import datetime, timezone

from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.routers import cv_parsing, job_analysis, matching, recommendation, skill_gap
from app.services.llm_client import LLMError, LLMNotConfiguredError

app = FastAPI(
    title="SmartRecruit AI Service",
    description="CV Parsing, Job Analysis, Matching, Recommendation, Skill Gap (IA-01 à IA-05).",
    version="0.1.0",
)

API_PREFIX = "/api/v1/ai"

app.include_router(cv_parsing.router, prefix=API_PREFIX)
app.include_router(job_analysis.router, prefix=API_PREFIX)
app.include_router(matching.router, prefix=API_PREFIX)
app.include_router(recommendation.router, prefix=API_PREFIX)
app.include_router(skill_gap.router, prefix=API_PREFIX)


@app.exception_handler(LLMNotConfiguredError)
async def llm_not_configured(_: Request, exc: LLMNotConfiguredError):
    return JSONResponse(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, content={"detail": str(exc)})


@app.exception_handler(LLMError)
async def llm_failed(_: Request, exc: LLMError):
    return JSONResponse(status_code=status.HTTP_502_BAD_GATEWAY, content={"detail": str(exc)})


@app.get(f"{API_PREFIX}/ping")
async def ping():
    return {
        "service": settings.service_name,
        "status": "UP",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
