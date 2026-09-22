from datetime import datetime, timezone

from fastapi import FastAPI

from app.core.config import settings
from app.routers import cv_parsing, job_analysis, matching, recommendation, skill_gap

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


@app.get(f"{API_PREFIX}/ping")
async def ping():
    return {
        "service": settings.service_name,
        "status": "UP",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
