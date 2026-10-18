"""Données d'exemple partagées par les tests de matching, recommandation et skill gap."""

import uuid
from datetime import date

from app.schemas.common import CVParsingResult, JobAnalysisResult, ParsedEducation, ParsedExperience
from app.schemas.enums import ExperienceLevel
from app.services.job_analysis_service import AnalyzedJob

CV = CVParsingResult(
    skills=["Java", "Spring Boot", "Postgres", "Docker"],
    experiences=[
        ParsedExperience(title="Backend Developer", company="Acme", start_date=date(2020, 1, 1),
                         end_date=None, description="Microservices Spring Boot"),
    ],
    education=[ParsedEducation(degree="Master Génie Logiciel", institution="ESPRIT", year=2019)],
    languages=["Français", "Anglais"],
)


def job(required_skills=("Java", "Spring Boot", "PostgreSQL", "Kubernetes"), title="Développeur Backend",
        level=ExperienceLevel.SENIOR, job_id=None) -> AnalyzedJob:
    return AnalyzedJob(
        job_id=job_id or uuid.uuid4(),
        title=title,
        description=f"{title} maîtrisant {', '.join(required_skills)}.",
        analysis=JobAnalysisResult(required_skills=list(required_skills), experience_level=level, seniority=level),
    )


EVALUATION = {
    "skills": {"subScore": 75, "explanation": "3 des 4 compétences requises (Kubernetes manque)."},
    "experience": {"subScore": 80, "explanation": "6 ans d'expérience backend pour un poste SENIOR."},
    "education": {"subScore": 90, "explanation": "Master en génie logiciel adapté au poste."},
}
