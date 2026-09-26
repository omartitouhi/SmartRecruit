from datetime import date
from pydantic import BaseModel, ConfigDict

from app.schemas.enums import ExperienceLevel


def _to_camel(field: str) -> str:
    head, *tail = field.split("_")
    return head + "".join(word.capitalize() for word in tail)


class CamelModel(BaseModel):
    """Base commune : sérialise en camelCase (DATA_MODEL.md §1) tout en gardant
    des noms de champs Python en snake_case."""

    model_config = ConfigDict(alias_generator=_to_camel, populate_by_name=True)


class MatchCriterion(CamelModel):
    label: str
    weight: float
    contribution: float
    explanation: str


class MatchScore(CamelModel):
    """Réponse de POST /api/v1/ai/matching/score — DATA_MODEL.md §4.1."""

    score: float
    criteria: list[MatchCriterion]


class ParsedExperience(CamelModel):
    title: str
    company: str
    start_date: date
    end_date: date | None = None
    description: str


class ParsedEducation(CamelModel):
    degree: str
    institution: str
    year: int


class CVParsingResult(CamelModel):
    """Réponse de POST /api/v1/ai/cv-parsing/parse — DATA_MODEL.md §4.2."""

    skills: list[str]
    experiences: list[ParsedExperience]
    education: list[ParsedEducation]
    languages: list[str]


class JobAnalysisResult(CamelModel):
    """Réponse de POST /api/v1/ai/job-analysis/analyze — DATA_MODEL.md §4.3."""

    required_skills: list[str]
    experience_level: ExperienceLevel
    seniority: ExperienceLevel


class SkillGapResult(CamelModel):
    """Réponse de GET /api/v1/ai/skill-gap/candidates/{id}/jobs/{jobId} — DATA_MODEL.md §4.4."""

    missing_skills: list[str]
